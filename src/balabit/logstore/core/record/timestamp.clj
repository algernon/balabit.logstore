(ns balabit.logstore.core.record.timestamp
  "LogStore timestamp record functions"

  (:require [balabit.logstore.core.record.common :as lgs-rec-common]
            [gloss.core]
            [gloss.io])
  (:use balabit.logstore.core.utils))

(defrecord LSTRecordTimestamp [header
                               chunk-id
                               timestamp])

(gloss.core/defcodec- record-timestamp
  (gloss.core/ordered-map
   :chunk-id :uint32
   :timestamp (gloss.core/header
               (gloss.core/compile-frame
                (gloss.core/ordered-map
                 :timestamp-size :uint32))
               (fn [header]
                 (gloss.core/compile-frame
                  (gloss.core/ordered-map
                   :data (gloss.core/finite-block (:timestamp-size header))
                   :padding (gloss.core/finite-block
                             (- 4096 6 4 4 (:timestamp-size header))))))
               0)))

(defmethod lgs-rec-common/read-record-data :timestamp
  [header handle]

  (let [original-pos (.position handle)
        ts (gloss.io/decode record-timestamp
                            (slice-n-dice handle 4090))]
    (.position handle (+ original-pos (:size header) -6))
    (LSTRecordTimestamp. header
                         (:chunk-id ts)
                         (-> ts :timestamp :data first))))
