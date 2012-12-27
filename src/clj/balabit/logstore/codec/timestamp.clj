(ns balabit.logstore.codec.timestamp

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (java.nio ByteBuffer))
  (:use [balabit.blobbity]
        [balabit.logstore.exceptions]
        [balabit.logstore.codec.verify]))

;; ### Timestamp records
;;
;; Timestamp records are used to apply cryptographically secure
;; timestamps to previous records within a LogStore.
;;
;; Each timestamp is against a given chunk, and the chunk id the
;; timestamp is for is stored as a 32-bit integer at the beginning of
;; the timestamp.
;;
;; Right now, the library does not process these records further than
;; extracting the chunk-id, and the timestamp itself as binary data.
;;
(defmethod decode-frame :logstore/record.timestamp
  [#^ByteBuffer buffer _ header & _]

  (let [timestamp (decode-blob buffer [:chunk-id :uint32
                                       :timestamp [:prefixed :slice :uint32]])]
    (decode-frame buffer :skip (- 4096 (.limit (:timestamp timestamp)) 14))
    (merge header timestamp)))
