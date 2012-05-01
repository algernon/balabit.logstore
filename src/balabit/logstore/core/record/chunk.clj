(ns balabit.logstore.core.record.chunk
  "LogStore chunk record functions.

   These are not meant to be used directly, but through
   `balabit.logstore.core.record`."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "GNU General Public License - v3"
              :url "http://www.gnu.org/licenses/gpl.txt"}}

  (:require [balabit.logstore.core.record.common :as lgs-rec-common]
            [gloss.core]
            [gloss.io])
  (:import (org.joda.time DateTime DateTimeZone)
           (java.io ByteArrayOutputStream InputStream OutputStream)
           (java.nio ByteBuffer)
           (java.net InetAddress)
           (java.util.zip InflaterInputStream Inflater))
  (:use [balabit.logstore.core.utils]))

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

;; A serialized message consists of a set of known properties, such as
;; its flags, its severity and facility, timestamps, tags and so on
;; and so forth. It also contains a collection of name-value pairs,
;; that contain the bulk of the information.
(defrecord LSTSerializedMessage [flags
                                 severity
                                 facility
                                 socket-address
                                 stamp
                                 recv-stamp

                                 tags
                                 nvpairs
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
  millis).

  If a timezone offset is specified too, it will be used too."

  ([secs usecs] (DateTime. (+ (* secs 1000) (quot usecs 1000))))
  ([secs usecs tzoffs] (DateTime. (+ (* secs 1000) (quot usecs 1000))
                                  (DateTimeZone/forOffsetMillis tzoffs))))

(defn stream-copy
  "Copy a stream from an `InputStream` to an `OutputStream`."
  [^InputStream input ^OutputStream output]
  (let [buffer (make-array Byte/TYPE 1024)]
    (loop []
      (let [size (.read input buffer)]
        (when (pos? size)
          (do (.write output buffer 0 size)
              (recur)))))))

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
  "Decompress a chunk, if so need be. Returns a `ByteBuffer`."
  (fn [record-header data-size data]
    (if (flag-set? record-header :compressed)
      :compressed
      :uncompressed)))

;; By default, when the :compressed flag is not set, we do not need to
;; do a thing, and as such, just return the original data.
(defmethod chunk-decompress :uncompressed
  [record-header data-size data] data)

;; If the dispatch function determined the :compressed flag is set,
;; then we need to decompress the data. We do this by using Java's
;; `InflaterInputStream`, with the added trick that we use a buffer
;; large enough to hold all the data. This is needed, because
;; otherwise decompression tends to fail horribly for some reason.
;;
;; Returns a `ByteBuffer` with the decompressed data.
(defmethod chunk-decompress :compressed
  [record-header data-size data]

  (let [buffer (ByteArrayOutputStream.)]
    (stream-copy (InflaterInputStream. (bb-buffer-stream data)
                                       (Inflater.) data-size) buffer)
    (ByteBuffer/wrap (.toByteArray buffer))))

;; ### Message deserialization
;; - - - - - - - - - - - - - -
;;
;; Each chunk contains multiple messages, and we want to return a
;; vector, where each element is a single message. To accomplish this,
;; we need a method to deserialize the messages.
;;
;; Depending on whether the message was stored in serialized or
;; unserialized format, the vector's elements will be either hash
;; maps, or strings, respectively.

(defmulti chunk-data-deserialize
  "Deserialize a message buffer."
  (fn [record-header data]
    (if (flag-set? record-header :serialized)
      :serialized
      :unserialized)))

;; #### Unserialized message splitting

;; An *unserialized* string is a set of bytes, with a 32-bit length
;; prefix.
(gloss.core/defcodec- unserialized-string
  (gloss.core/finite-frame :uint32 (gloss.core/string :utf-8)))

;; The default case is when the messages are not serialized, in which
;; case they're just strings with a 32-bit length-prefix. We'll use
;; the codec above to split the buffer into individual strings.
;;
;; Returns a vector of Strings.
(defmethod chunk-data-deserialize :unserialized
  [record-header data]

  (gloss.io/decode-all unserialized-string data))

;; #### Serialized message deserialization and splitting

;; Each serialized message begins with a fairly complex header, which
;; is a bit awkward to parse: it's not easy to figure out the length
;; of the socket address and the tags.
;;
;; For reasons above, deserializing a serialized message is split into
;; multiple parts:

;; To read the beginning of the header, up to and including the socket
;; family, we need a simple codec.
(gloss.core/defcodec- serialized-msg-header-begin
  (gloss.core/ordered-map
   :length :uint32
   :version :byte,
   :flags :uint32,
   :priority :uint16
   :socket-family :uint16))

(defn- serialized-msg-header-get-begin
  "Read the beginning of the header of a serialized message, and
  position the buffer to the byte after the read data.

  Returns the parsed header part."
  [#^ByteBuffer buffer]

  (let [hdr-begin (gloss.io/decode serialized-msg-header-begin
                                   (slice-n-dice buffer 13))]
    (.position buffer (+ (.position buffer) 13))
    hdr-begin))

;; Following the first part of the header, is the socket address. The
;; length and contents depends on the socket type, so it needs special
;; handling.

(defn- sockaddr-len
  "Given a socket family, return the length of its binary
  representation, with a port number attached."
  [family]

  (cond
   (= family 2) 6
   (= family 10) 18
   :else 0))

(defn- serialized-msg-header-decode-sockaddr
  "Decodes the socket address of `family` family from the
  `buffer`. Returns the parsed address and port, if any."
  [buffer family]

  (cond
   (= family 2) (gloss.io/decode
                 (gloss.core/compile-frame
                  (gloss.core/ordered-map
                   :address (gloss.core/finite-block 4)
                   :port :uint16))
                 (slice-n-dice buffer (sockaddr-len family)))
   (= family 10) (gloss.io/decode
                  (gloss.core/compile-frame
                   (gloss.core/ordered-map
                    :address (gloss.core/finite-block 16)
                    :port :uint16))
                  (slice-n-dice buffer (sockaddr-len family)))
   :else nil))

(defn- serialized-msg-header-get-sockaddr
  "Retrieves the socket address from the buffer, and moves the buffer
  position to after the address. Returns the decoded address and port,
  along with the socket family."
  [#^ByteBuffer buffer family]

  (let [sa (serialized-msg-header-decode-sockaddr buffer family)]
    (.position buffer (+ (.position buffer) (sockaddr-len family)))
    (into {:family family} sa)))

;; Following the socket address, come two timestamps: the timestamp of
;; the message, and the timestamp it was received at.

;; The timestamp itself is made up from three parts: the seconds part,
;; microseconds, and a time-zone offset. We define an ordered map, so
;; that we won't have to repeat ourselves for both stamps in the codec
;; below.
(def serialized-stamp
  (gloss.core/ordered-map
   :sec :uint64,
   :usec :uint32,
   :zone-offset :uint32))

;; As explained just above, the serialized message header contains two
;; timestamps, with the format described in `serialized-stamp`.
(gloss.core/defcodec- serialized-msg-header-stamps
  (gloss.core/ordered-map
   :stamp serialized-stamp,
   :recv-stamp serialized-stamp))

;; The hardest part of parsing the header is without doubt the tags: a
;; set of length-prefixed strings, where an empty string signals the
;; end of the list.

(defn- serialized-msg-header-get-tag
  "Read a tag from the buffer, and move its position after the
  tag. Returns a String."
  [#^ByteBuffer buffer]

  (let [tag-length (gloss.io/decode (gloss.core/compile-frame :uint32)
                                    (slice-n-dice buffer 4))
        _ (.position buffer (+ (.position buffer) 4))]
    (if (> tag-length 0)
      (let [byte-array (byte-array tag-length)]
        (.get buffer byte-array 0 tag-length)
        (String. byte-array))
      nil)))

(defn- serialized-msg-header-get-tags
  "Read all tags from the buffer. Returns a vector of Strings."
  [buffer]

  (loop [tags []]
    (let [tag (serialized-msg-header-get-tag buffer)]
      (if (empty? tag)
        tags
        (recur (conj tags tag))))))

;; At the end of the header, after the tags, are a few more
;; properties, which we'll need later on.
(gloss.core/defcodec- serialized-msg-header-trail
  (gloss.core/ordered-map
   :initial-parse :byte
   :num-matches :byte
   :num-sdata :byte
   :alloc-sdata :byte))

(defn- serialized-msg-header-read
  "Read the full headers of a serialized message. Returns all the
  relevant info combined into a hash-map. Also positions the buffer to
  the end of the header."
  [#^ByteBuffer data]

  (let [begin (serialized-msg-header-get-begin data)
        sockaddr (serialized-msg-header-get-sockaddr data (:socket-family begin))
        stamps (gloss.io/decode serialized-msg-header-stamps
                                (slice-n-dice data 32))
        _ (.position data (+ (.position data) 32))
        tags (serialized-msg-header-get-tags data)
        trail (gloss.io/decode serialized-msg-header-trail
                               (slice-n-dice data 4))
        _ (.position data (+ (.position data) 4))
        ]
    (merge (dissoc begin :socket-family)
           {:socket-address sockaddr}
           stamps
           {:tags tags}
           trail)))

(defn- resolve-address
  "Given an internet address in binary form (either IPv4 or IPv6),
  turn it into a string with the address represented in
  dotted-notation."
  [#^ByteBuffer addr]

  (let [buffer (byte-array (.limit addr))]
    (.get addr buffer 0 (.limit addr))
    (InetAddress/getByAddress buffer)))

(defn- resolve-socket-family
  "Turns the binary representation of the socket family into a
  symbolic name."
  [family]

  (cond
   (= family 2) :inet4
   (= family 10) :inet6
   :else :unknown))

;; To help turning a priority (facility + severity combined) into a
;; symbolic facility name, we define a vector of all known syslog
;; facilities.
(def facility-map #^{:private true}
  [:kern
   :user
   :mail
   :daemon
   :auth
   :syslog
   :lpr
   :news
   :uucp
   :cron
   :authpriv
   :ftp
   :ntp
   :log-audit
   :log-alert
   :clock2
   :local0
   :local1
   :local2
   :local3
   :local4
   :local5
   :local6
   :local7])

;; Similar to `facility-map`, the other part of the priority is looked
;; up from this table, that contains the known syslog severities.
(def severity-map #^{:private true}
  [:emergency
   :alert
   :critical
   :error
   :warning
   :notice
   :informational
   :debug])

(defn- serialized-msg-read
  "Read a single serialized message, and parse it. Returns an
  `LSTSerializedMessage`."
  [#^ByteBuffer buffer]

  (let [start-pos (.position buffer)
        header (serialized-msg-header-read buffer)
        facility (quot (:priority header) 8)
        severity (rem (:priority header) 8)]
    (.position buffer (+ start-pos (:length header) 4))
    (LSTSerializedMessage. (:flags header)
                           (severity-map severity)
                           (facility-map facility)
                           (merge
                            (:socket-address header)
                            {:address (resolve-address
                                       (-> header :socket-address :address first))
                             :family (resolve-socket-family
                                      (-> header :socket-address :family))})
                           (translate-timestamp (long (-> header :stamp :sec))
                                                (-> header :stamp :usec)
                                                (* (-> header :stamp :zone-offset) 1000))
                           (translate-timestamp (long (-> header :recv-stamp :sec))
                                                (-> header :recv-stamp :usec)
                                                (* (-> header :recv-stamp :zone-offset) 1000))

                           (:tags header)
                           nil)))

;; This impmenetation of the `chunk-data-deserialize` function is for
;; chunks which have the `:serialized` flag set. It reads through the
;; data buffer, and deserializes each message one by one, returning a
;; vector of `LSTSerializedMessage` instances in the end.
(defmethod chunk-data-deserialize :serialized
  [record-header #^ByteBuffer data]

  (loop [messages []]
    (if (>= (.position data) (.limit data))
      messages
      (recur (conj messages (serialized-msg-read data))))))

;; ### Chunk decoding
;; - - - - - - - - -

(defn- chunk-decode
  "Does everything neccessary to turn the raw message data into
  something more comprehensible, something easier to work with.

  This is done by composing all of the multi-methods above."
  [header msgcnt data data-size]
  ((comp (partial chunk-data-deserialize header)
         (partial chunk-decompress header data-size))
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
     :chunk-hmac (gloss.core/finite-block :uint32))))

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
  [header #^ByteBuffer handle]

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

