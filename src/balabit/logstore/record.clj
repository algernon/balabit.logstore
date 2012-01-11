(ns balabit.logstore.record
  "LogStore record functions.")

(defrecord LSTRecordHeader [offset size type flags])

(defn read-header
  "Read the header of a LogStore record. Returns an LSTRecordHeader."
  [lst]

  (let [handle (:handle lst)]
    (LSTRecordHeader. (.position handle)
                      (.getInt handle) ; size
                      (.get handle) ; type
                      (.get handle) ; flags
                    )))
