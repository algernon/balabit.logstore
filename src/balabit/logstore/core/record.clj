(ns balabit.logstore.core.record
  "Generic LogStore record functions.

   This is the main entry-point of the LogStore record handling code,
   as it encapsulates all the namespaces below it, providing a
   higher-level, generic interface to the differring record reader
   implementations."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "GNU General Public License - v3"
              :url "http://www.gnu.org/licenses/gpl.txt"}}

  (:refer-clojure :exclude [read])
  (:use [balabit.logstore.core.utils :only [resolve-flags, slice-n-dice]]
        [balabit.logstore.core.record.chunk]
        [balabit.logstore.core.record.timestamp])
  (:require [gloss.core]
            [gloss.io]
            [balabit.logstore.core.record.common :as lgs-rec-common])
  (:import (balabit.logstore.core.record.common LSTRecordHeader)))

;; ## Maps, bitmaps, etc.
;; - - - - - - - - - - -

;; Map to help resolving the binary representation of a record type to
;; a symbolic name. See `resolve-type`.
(def type-map #^{:private true}
  [:xfrm-info
   :chunk
   :timestamp])

;; A map of flag bits present in a LogStore record. This will be used
;; to turn the flag bit into a vector of symbolic flags.
(def record-flag-bitmap #^{:private true}
  [:compressed
   :encrypted
   :broken
   :serialized])

;; ## Helper functions
;; - - - - - - - - - -

(defn- resolve-type
  "Resolve the type of a LogStore record, and turning it into a
  symbolic name. Returns `:unknown` if the type is not otherwise
  known."
  [int-type]

  (if (and (>= (count type-map) int-type)
           (> int-type 0))
    (nth type-map (dec int-type))
    :unknown))

;; ## Published functions
;; - - - - - - - - - - -

(defn read-header
  "Read the header of a LogStore record. Returns an `LSTRecordHeader`."
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
  "Read a record from a LogStore. The exact return value depends on
  the type of the record: it can be any of `LSTRecordChunk` or
  `LSTRecordTimestamp`."
  [lst]

  (lgs-rec-common/read-record-data (read-header lst) (:handle lst)))
