(ns balabit.logstore.core.record
  "LogStore record functions."

  (:refer-clojure :exclude [read])
  (:use [balabit.logstore.core.utils :only [resolve-flags, slice-n-dice]])
  (:use balabit.logstore.core.record.chunk)
  (:require [gloss.core]
            [gloss.io]
            [balabit.logstore.core.record.common :as lgs-rec-common])
  (:import (balabit.logstore.core.record.common LSTRecordHeader)))

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

(defn read-header
  "Read the header of a LogStore record. Returns an LSTRecordHeader."
  [lst]

  (let [offset (.position (:handle lst))
        hdr (gloss.io/decode lgs-rec-common/record-common-header
                             (slice-n-dice (:handle lst) 6))]
    (.position (:handle lst) (+ offset 6))
    (LSTRecordHeader. offset
                      (:size hdr)
                      (resolve-type (:type hdr))
                      (resolve-flags (:flags hdr) record-flag-bitmap))))

(defn read
  "Read a record from a LogStore. Returns an LSTRecord."
  [lst]

  (lgs-rec-common/read-record-data (read-header lst) (:handle lst)))
