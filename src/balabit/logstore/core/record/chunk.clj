(ns balabit.logstore.core.record.chunk
  "LogStore chunk record functions.

   These are not meant to be used directly, but through
   `balabit.logstore.core.record`."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license "All rights reserved"}

  (:require [balabit.logstore.core.record.common :as lgs-rec-common]
            [gloss.core]
            [gloss.io])
  (:import (org.joda.time DateTime)
           (java.io DataInputStream)
           (java.util.zip InflaterInputStream Inflater))
  (:use balabit.logstore.core.utils))

;; ## Records, bitmaps, etc
;; - - - - - - - - - - - -

;; A record of interesting and useful information about a `:chunk`
;; type record in a LogStore file. Contains all the information the
;; end-user needs to make use of the record.
;;
;; The most interesting part is the `messages` field, which contains a
;; vector of messages. The messages are either strings, or (once
;; implemented) hash maps.
(defrecord LSTRecordChunk [header
                           start-time end-time
                           first-msgid last-msgid
                           chunk-id
                           xfrm-offset

                           messages
                           flags
                           file-mac
                           chunk-mac
                           ])

;; Known chunk flags, used to turn a binary representation into a
;; vector of symbolic names.
(def chunk-flag-bitmap #^{:private true}
  [:hmac
   :hash])

;; ## Helper functions
;; - - - - - - - - - -

(defn- translate-timestamp
  "Translate a timestamp consisting of a 64bit seconds part, and a
  32bit microsecond part into a single value, into a DateTime
  object. It does loose a little bit of precision (it can only handle
  millis)."
  [secs usecs]

  (DateTime. (+ (* secs 1000) (quot usecs 1000))))

;; ## Message deserialization
;; - - - - - - - - - - - - -
;;
;; Messages are stored in either serialized or unserialized form in a
;; chunk, they're also either compressed or not, encrypted or
;; unencrypted. This lends itself well to splitting the work into
;; multiple stages, and using function composition in the end to do
;; them all.
;;
;; The idea is that we use multi-methods, that either return their
;; input doing nothing, or process it - all depending on whether the
;; flag they're handling is set or not.

;; ### Decompression support
;; - - - - - - - - - - - - -
;;

(defmulti chunk-decompress
  "Decompress a chunk, if so need be."
  (fn [record-header data-size data] (flag-set? record-header :compressed)))

;; By default, when the :compressed flag is not set, we do not need to
;; do a thing, and as such, just return the original data.
(defmethod chunk-decompress :default
  [record-header data-size data] data)

;; If the dispatch function determined the :compressed flag is set,
;; then we need to decompress the data. We do this by using Java's
;; `InflaterInputStream`, with the added trick that we use a buffer
;; large enough to hold all the data. This is needed, because
;; otherwise decompression tends to fail horribly for some reason.
;;
;; Returns an instance of `InflaterInputStream`, which is thankfully
;; castable to `DataInputStream` (see `chunk-data-split` later).
(defmethod chunk-decompress true
  [record-header data-size data]
  (InflaterInputStream. data (Inflater.) data-size))

;; ### Message splitting
;; - - - - - - - - - - -
;;
;; Each chunk contains multiple messages, and we want to return a
;; record, where each element is a single message. To accomplish this,
;; we need a method to split the data into a vector.

(defmulti chunk-data-split
  "Split unserialized data into chunks."
  (fn [record-header msgcnt data] (flag-set? record-header :serialized)))

(defn- chunk-data-read-line
  "Read a length-prefixed log line from a DataInputStream."
  [stream]

  (try
    (let [len (.readInt stream)]
      (let [buffer (byte-array len)]
        (.read stream buffer)
        buffer))
    (catch Exception _
      nil)))

;; The default case is when the messages are not serialized, in which
;; case they're just strings with a 32-bit length-prefix.
;;
;; We do the split by reading the whole data stream and split the
;; strings up into a vector.
;;
;; Returns a vector of byte arrays.
(defmethod chunk-data-split :default
  [record-header msgcnt data]

  (let [stream (DataInputStream. data)]
    (loop [messages []
           remaining msgcnt]
      (let [line (chunk-data-read-line stream)]
        (if (< remaining 0)
          messages
          (recur (conj messages line) (dec remaining)))))))

;; The harder case is when log messages are serialized, and
;; deserializing those is not implemented yet, so this function just
;; returns the data-as is.
(defmethod chunk-data-split :serialized
  [record-header msgcnt data] data)

;; ### Message stringification
;; - - - - - - - - - - - - - -
;;
;; In the end, we also want to turn the log messages into strings
;; (unless serialized), and we use another multi-method to accomplish
;; this task.

(defmulti chunk-data-stringify
  "Cast all members of data to Strings."
  (fn [record-header data] (flag-set? record-header :serialized)))

;; The default is - again - that messages are unserialized. We get a
;; vector of byte-arrays, or something else that can be used as a
;; constructor for Strings, and return a vector of Strings.
(defmethod chunk-data-stringify :default
  [record-header data]

  (map #(String. %) data))

;; For serialized data, however, we do nothing: we already have the
;; preferred form: a hash-map.
(defmethod chunk-data-stringify true
  [record-header data] data)

;; ### Chunk decoding
;; - - - - - - - - -

(defn- chunk-decode
  "Does everything neccessary to turn the raw message data into
  something more comprehensible, something easier to work with.

  This is done by composing all of the multi-methods above."
  [header msgcnt data data-size]
  ((comp (partial chunk-data-stringify header)
         (partial chunk-data-split header msgcnt)
         (partial chunk-decompress header data-size)
         bb-buffer-stream)
   data))

;; ## Codecs
;; - - - - -

;; The chunk is parsed in two steps: first we parse it until the
;; `tail-offset` element, and that is what this codec does.
(gloss.core/defcodec- record-chunk-header
  (gloss.core/ordered-map
   :start-time :uint64
   :start-time-usec :uint32
   :end-time :uint64
   :end-time-usec :uint32
   :first-msgid :uint32
   :last-msgid :uint32
   :chunk-id :uint32
   :xfrm-offset :uint64
   :tail-offset :uint32))

;; The other part of the chunk contains the actual chunk data,
;; followed by flags and cryptographic hashes. We need the value of
;; `tail-offset` to calculate the length of `chunk-data`, however,
;; therefore the codec was split in two, and the second part made into
;; a macro.
(defmacro record-chunk-assemble-trail
  [header]
  
  `(gloss.core/compile-frame
    (gloss.core/ordered-map
     :chunk-data (gloss.core/finite-block (- (:tail-offset ~header) 54)),
     :flags :uint32,
     :file-mac (gloss.core/finite-block :uint32)
     :chunk-hmac (gloss.core/finite-block :uint32)
     )))

;; ## Published functions
;; - - - - - - - - - - -

;; Extends the `read-record-data` multi-method in
;; `balabit.logstore.core.record.common` for the `:chunk` record type.
;;
;; It not only reads the chunk data, but also processes the message
;; part, to turn it into a format that is easier to work with (a
;; vector of Strings or hash-maps; see the message deserialization
;; part just above).
(defmethod lgs-rec-common/read-record-data :chunk
  [header handle]

  (let [original-pos (.position handle)
        chunk-header (gloss.io/decode record-chunk-header
                                      (slice-n-dice handle 48))
        raw-data (slice-n-dice handle (+ original-pos 48)
                               (- (:size header) 54))
        chunk-trail (gloss.io/decode
                     (record-chunk-assemble-trail chunk-header)
                     raw-data)]
    (.position handle (+ original-pos (:size header) -6))
    (LSTRecordChunk. header
                     (translate-timestamp (long (:start-time chunk-header))
                                          (:start-time-usec chunk-header))
                     (translate-timestamp (long (:end-time chunk-header))
                                          (:end-time-usec chunk-header))
                     (:first-msgid chunk-header) (:last-msgid chunk-header)
                     (:chunk-id chunk-header)
                     (:xfrm-offset chunk-header)
                     (chunk-decode header (- (:last-msgid chunk-header)
                                             (:first-msgid chunk-header))
                                   (first (:chunk-data chunk-trail))
                                   (- (:tail-offset chunk-header) 54))
                     (resolve-flags (:flags chunk-trail) chunk-flag-bitmap)
                     (:file-mac chunk-trail)
                     (:chunk-hmac chunk-trail))))

