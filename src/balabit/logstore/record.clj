(ns balabit.logstore.record
  "LogStore record functions."

  (:refer-clojure :exclude [read])
  (:use balabit.logstore.utils)
  (:import (org.joda.time DateTime DateTimeZone))
)

(defprotocol IRecordHeader
  "LogStore record header protocol. Everything that is a LogStore
record, or a descendant of it, must implement this."
  (flag-set? [this flag]
    "Determines whether a given flag is set on a LogStore record header"))

(defrecord LSTRecordHeader [offset size type flags]
  IRecordHeader
  (flag-set? [this flag]
    (or (some #(= flag %) (:flags this)) false)))

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
                              start_time end_time
                              first_msgid last_msgid
                              chunk_id
                              xfrm_offset
                              
                              ;chunk_tail_end

                              data
                              flags
                              file_mac
                              chunk_mac
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

  (let [handle (:handle lst)]
    (LSTRecordHeader. (.position handle)
                      (.getInt handle) ; size
                      (resolve-type (.get handle)) ; type
                      (resolve-flags (.get handle) record-flag-bitmap) ; flags
                    )))

(defmulti read-record-data
  "Read a given type of record from a LogStore ByteBuffer. The record
header is already parsed, and available as record-header, the buffer
behind the handle is positioned right after the record header."
  (fn [record-header handle] (:type record-header)))

;; Default implementation of the record data reader. It just reads the
;; whole record, and returns a generic LSTRecord.
(defmethod read-record-data :default
  [header handle]
  (LSTRecord. header (.limit (.slice handle) (- (:size header) 6))))

(defn- read-timestamp
  "Read a timestamp from a ByteBuffer"
  [handle]

  (let [sec (* (.getLong handle) 1000)
        usec (.getInt handle)]
    (+ sec (quot usec 1000))))

(def chunk-flag-bitmap #^{:private true}
  [:hmac
   :hash])

;; Reads :chunk type LogStore records, and parses the sub-header, and
;; will handle data demungling too, at a later time.
(defmethod read-record-data :chunk
  [header handle]

  (let [original_pos (.position handle)
        buffer (.limit (.slice handle) (- (:size header) 6))
        start_time (DateTime. (read-timestamp handle))
        end_time (DateTime. (read-timestamp handle))
        first_msgid (.getInt handle)
        last_msgid (.getInt handle)
        chunk_id (.getInt handle)
        xfrm_offset (.getLong handle)
        tail_offset (.getInt handle)
        data (.limit (.slice handle) (- tail_offset 48))
        flags (do (.position handle (+ original_pos tail_offset -6)) (.getInt handle))
        ]
    (LSTRecordChunk. header
                     start_time end_time
                     first_msgid last_msgid
                     chunk_id
                     xfrm_offset
                     data
                     (resolve-flags flags chunk-flag-bitmap)
                     (bb-read-block handle) ;file_mac
                     (bb-read-block handle) ;chunk_mac
                     )))

(defn read
  "Read a record from a LogStore. Returns an LSTRecord."
  [lst]

  (read-record-data (read-header lst) (:handle lst)))
