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

(defn read-header
  "Read the header of a LogStore record. Returns an LSTRecordHeader."
  [lst]

  (let [handle (:handle lst)]
    (LSTRecordHeader. (.position handle)
                      (.getInt handle) ; size
                      (resolve-type (.get handle)) ; type
                      (.get handle) ; flags
                    )))

(defn read
  "Read a record from a LogStore. Returns an LSTRecord."
  [lst]

  (let [header (read-header lst)
        handle (:handle lst)
        record (.slice handle)]
    (.limit record (- (:size header) 6))
    (LSTRecord. header record)))
