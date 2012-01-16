(ns balabit.logstore.core.record
  "LogStore record functions."

  (:refer-clojure :exclude [read])
  (:use balabit.logstore.core.utils)
  (:use [slingshot.slingshot :only [throw+]])
  (:require [gloss.core]
            [gloss.io])
  (:import (org.joda.time DateTime DateTimeZone)
           (java.io DataInputStream)
           (java.util.zip InflaterInputStream Inflater)))

(defprotocol IRecordHeader
  "LogStore record header protocol. Everything that is a LogStore
record, or a descendant of it, must implement this."
  (flag-set? [this flag]
    "Determines whether a given flag is set on a LogStore record header"))

(defrecord LSTRecordHeader [offset size type flags]
  IRecordHeader
  (flag-set? [this flag]
    (or (some #(= flag %) (:flags this)) false)))

(gloss.core/defcodec record-common-header
  (gloss.core/ordered-map
   :size :uint32
   :type :byte
   :flags :byte))

(defmacro defrecheader
  "Crete a record, descending from IRecordHeader, that has a default
  flag-set? implementation."
  [name fields]

  `(do
     (defrecord ~name ~fields
       IRecordHeader
       (flag-set? [~'this ~'flag]
         (flag-set? (:header ~'this) ~'flag)))))

(defrecheader LSTRecord [header data])
(defrecheader LSTRecordChunk [header
                              start-time end-time
                              first-msgid last-msgid
                              chunk-id
                              xfrm-offset
                              
                              messages
                              flags
                              file-mac
                              chunk-mac
                              ])

(def type-map #^{:private true}
  [:xfrm-info
   :chunk
   :timestamp])

(defn- resolve-type
  "Resolve the type of a LogStore record."
  [int-type]

  (if (and (> (count type-map) int-type)
           (> int-type 0))
    (nth type-map (dec int-type))
    :unknown))

(def record-flag-bitmap #^{:private true}
  [:compressed
   :encrypted
   :broken
   :serialized])

(defn- bitmap-find
  "Find out whether a bit is set in a Number, if so, add the same
  index entry from a bitmap to the accumulator, otherwise don't touch
  it. Returns the accumulator."
  [acc bitmap x n]
  (if (bit-test x n)
    (conj acc (nth bitmap n))
    acc))

(defn- resolve-flags
  "Expand flags bitwise-OR'd together into symbolic names, using a
  bitmap table."
  [int-flags bitmap]

  (loop [index 0
         acc []]
    (if (< index (count bitmap))
      (recur (inc index) (bitmap-find acc bitmap int-flags index))
      acc)))

(defn read-header
  "Read the header of a LogStore record. Returns an LSTRecordHeader."
  [lst]

  (let [offset (.position (:handle lst))
        hdr (gloss.io/decode record-common-header (slice-n-dice (:handle lst) 6))]
    (.position (:handle lst) (+ offset 6))
    (LSTRecordHeader. offset
                      (:size hdr)
                      (resolve-type (:type hdr))
                      (resolve-flags (:flags hdr) record-flag-bitmap))))

(defmulti read-record-data
  "Read a given type of record from a LogStore ByteBuffer. The record
header is already parsed, and available as record-header, the buffer
behind the handle is positioned right after the record header."
  (fn [record-header handle] (:type record-header)))

(defn- translate-timestamp
  "Translate a timestamp consisting of a 64bit seconds part, and a
  32bit microsecond part into a single value, into a DateTime
  object. It does loose a little bit of precision (it can only handle
  millis)."
  [secs usecs]

  (DateTime. (+ (* secs 1000) (quot usecs 1000))))

;;
;; # Chunk format handling
;;

(def chunk-flag-bitmap #^{:private true}
  [:hmac
   :hash])

(defmulti chunk-decompress
  "Decompress a chunk, if so need be."
  (fn [record-header data-size data] (flag-set? record-header :compressed)))

(defmethod chunk-decompress :default
  [record-header data-size data] data)

(defmethod chunk-decompress true
  [record-header data-size data]
  (InflaterInputStream. data (Inflater.) data-size))

(defmulti chunk-data-split
  "Split unserialized data into chunks"
  (fn [record-header msgcnt data] (flag-set? record-header :serialized)))

(defmethod chunk-data-split :serialized
  [record-header msgcnt data] data)

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

(defmethod chunk-data-split :default
  [record-header msgcnt data]

  (let [stream (DataInputStream. data)]
    (loop [messages []
           remaining msgcnt]
      (let [line (chunk-data-read-line stream)]
        (if (< remaining 0)
          messages
          (recur (conj messages line) (dec remaining)))))))

(defn- chunk-data-stringify
  "Cast all members of data to Strings."
  [data]

  (map #(String. %) data))

(defn- chunk-decode
  [header msgcnt data data-size]
  ((comp chunk-data-stringify
         (partial chunk-data-split header msgcnt)
         (partial chunk-decompress header data-size)
         bb-buffer-stream)
   data))


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

(defmacro record-chunk-assemble-trail
  [header]
  
  `(gloss.core/compile-frame
    (gloss.core/ordered-map
     :chunk-data (gloss.core/finite-block (- (:tail-offset ~header) 54)),
     :flags :uint32,
     :file-mac (gloss.core/finite-block :uint32)
     :chunk-hmac (gloss.core/finite-block :uint32)
     )))

;; Reads :chunk type LogStore records, and parses the sub-header, and
;; will handle data demungling too, at a later time.
(defmethod read-record-data :chunk
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

(defn read
  "Read a record from a LogStore. Returns an LSTRecord."
  [lst]

  (read-record-data (read-header lst) (:handle lst)))
