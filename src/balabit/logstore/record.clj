(ns balabit.logstore.record
  "LogStore record functions."

  (:refer-clojure :exclude [read]))

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

(defn- resolve-record-flags
  "Expand the record flag into symbolic names"
  [int-flags]

  (loop [index 0
         acc []]
    (if (< index (count record-flag-bitmap))
      (recur (inc index) (bitmap-find acc record-flag-bitmap int-flags index))
      acc)))

(defn read-header
  "Read the header of a LogStore record. Returns an LSTRecordHeader."
  [lst]

  (let [handle (:handle lst)]
    (LSTRecordHeader. (.position handle)
                      (.getInt handle) ; size
                      (resolve-type (.get handle)) ; type
                      (resolve-record-flags (.get handle)) ; flags
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

(defn read
  "Read a record from a LogStore. Returns an LSTRecord."
  [lst]

  (read-record-data (read-header lst) (:handle lst)))
