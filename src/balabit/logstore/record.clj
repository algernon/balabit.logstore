(ns balabit.logstore.record
  "LogStore record functions."

  (:refer-clojure :exclude [read]))

(defrecord LSTRecordHeader [offset size type flags])
(defrecord LSTRecord [header data])

(def type-map #^{:private true}
  [:unknown
   :xfrm-info
   :chunk
   :timestamp])

(defn- resolve-type
  "Resolve the type of a LogStore record."
  [int-type]

  (if (and (> (count type-map) int-type)
           (> int-type 0))
    (nth type-map int-type)
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

(defn read
  "Read a record from a LogStore. Returns an LSTRecord."
  [lst]

  (let [header (read-header lst)
        handle (:handle lst)
        record (.slice handle)]
    (.limit record (- (:size header) 6))
    (LSTRecord. header record)))
