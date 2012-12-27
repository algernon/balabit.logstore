(ns balabit.logstore.codec.chunk

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (java.util.zip InflaterInputStream Inflater)
           (java.nio ByteBuffer)
           (java.io ByteArrayOutputStream FileInputStream InputStream OutputStream))
  (:use [balabit.blobbity]
        [balabit.logstore.utils]
        [balabit.logstore.exceptions]
        [balabit.logstore.codec.common]
        [balabit.logstore.codec.verify]
        [balabit.logstore.codec.chunk.serialization])
  (:require [balabit.logstore.codec.chunk.sweet :as chunk]))

;; ### Chunks, the bread and butter of a LogStore
;;
;; Chunks are the heart of LogStore, they contain our messages, they
;; hold most information that is of interest to us.
;;
;; The chunk has [extra headers][lgs/chunk-head] after the
;; [common record header][lgs/ch], and also sports a
;; [tail][lgs/chunk-tail] at the end, with [messages][lgs/chunk-messages]
;; inbetween.
;;
;; From all these, we assemble the records map: merge the headers and
;; the tail, with the messages added under a `:messages` key.
;;
;; [lgs/chunk-head]: #lgs/chunk-head
;; [lgs/chunk-tail]: #lgs/chunk-tail
;; [lgs/chunk-messages]: #lgs/chunk-messages
;;
(defmethod decode-frame :logstore/record.chunk
  [#^ByteBuffer buffer _ header]

  (let [chunk-head (merge header (decode-frame buffer :logstore/record.chunk-head))
        messages (decode-frame buffer :logstore/record.chunk-messages chunk-head)
        chunk-tail (decode-frame buffer :logstore/record.chunk-tail)]
    (->
     chunk-head

     (assoc :messages messages)
     (merge chunk-tail)
     (update-in [:flags] (partial apply conj (:flags chunk-head))))))

;; ### <a name="lgs/chunk-head">The chunk header</a>
;;
;; The chunk header starts with two timestamps: the `:start-time` and
;; the `:end-time` of the messages within the record, both are made up
;; of two parts: a 64-bit second counter, and a 32-bit microsecond one.
;;
;; After the timestamps, the header contains the `:first-id` and
;; `:last-id` (both 32-bit integers), which are the IDs of the first
;; and last message within the chunk, respectively.
;;
;; Past the message range, we have the 32-bit `:chunk-id`, followed by
;; a set of offsets: one to the `:xfrm` frame (if any, 0 otherwise),
;; and another to the beginning of the [tail][lgs/chunk-tail].
;;
;; The timestamps are converted to a suitable Java object, otherwise
;; the header is left intact.
;;
(defmethod decode-frame :logstore/record.chunk-head
  [#^ByteBuffer buffer _]

  (-> (decode-blob buffer [:start-time [:struct [:sec :uint64,
                                                 :usec :uint32]],
                           :end-time [:struct [:sec :uint64,
                                               :usec :uint32]],
                           :msg-limits [:struct [:first-id :uint32,
                                                 :last-id :uint32]],
                           :chunk-id :uint32,
                           :offsets [:struct [:xfrm :uint64,
                                              :tail :uint32]]])

      (update-in [:start-time] resolve-timestamp)
      (update-in [:end-time] resolve-timestamp)))

;; ### <a name="lgs/chunk-taiol">The chunk tail</a>
;;
;; At the end of a chunk record, there's the tail, which starts with a
;; 32-bit integer for various `:flags` (namely, `:hmac` and `:hash`,
;; neither of which is used by this library at this time), which are
;; followed by two checksums: one for the whole file so far, and
;; another for this chunk only. Both are prefixed by a 32-bit length.
;;
(defmethod decode-frame :logstore/record.chunk-tail
  [#^ByteBuffer buffer _]

  (->
   (decode-blob buffer [:flags :uint32,
                        :macs [:struct [:file-mac :logstore/common.mac
                                        :chunk-hmac :logstore/common.mac]]])

   (update-in [:flags] (partial resolve-flags [:hmac :hash]))))

;; ### <a name="lgs/chunk-messages">Messages within a chunk</a>
;;
;; Deserializing a message is far more work than what we've seen so
;; far: messages can be compressed or serialized, or even both, and
;; they need to be handled transparently.
;;
;; To this end, we introduce a couple of helper functions:
;;

(defn chunk-data-decompress
  "Given a chunk data and its header, decompress it if
  necessary. Returns the uncompressed data as a ByteBuffer."

  [data data-size header]

  (if (chunk/compressed? header)
    (let [buffer (ByteArrayOutputStream.)]
      (stream-copy (InflaterInputStream. (->InputStream data)
                                         (Inflater.) data-size) buffer)
      (ByteBuffer/wrap (.toByteArray buffer)))
    data))

(defn chunk-data-deserialize
  "Given some chunk data and its header, deserialize it. Returns a
  list of deserialized messages, where each message itself is a map."

  [data header]

  (if (chunk/serialized? header)
    (decode-blob-array data :chunk/message.serialized)
    (decode-blob-array data :chunk/message.unserialized)))

(defn chunk-decode
  "Given the chunk header, the data-size and the data itself, decode
  it. This is simply an composition of `chunk-data-decompress` and
  `chunk-data-deserialize`."

  [header data-size data]

  (-> data
      (chunk-data-decompress data-size header)
      (chunk-data-deserialize header)))

;; Armed with these helper functions, we can now decode the messages
;; in a chunk! We know its size: it is the offset of the tail, minus
;; the size of the full header.
;;
;; What we do here, is we extract the messages part of the input
;; buffer, and run it through `chunk-decode`.
;;
;; To understand how deserialization works, please see
;; [balabit.logstore.codec.chunk.serialization][serialization]!
;;
;; [serialization]: #balabit.logstore.codec.chunk.serialization
;;
(defmethod decode-frame :logstore/record.chunk-messages
  [#^ByteBuffer buffer _ header]

  (let [size (- (-> header :offsets :tail) 54)]
    (chunk-decode header size (decode-frame buffer :slice size))))
