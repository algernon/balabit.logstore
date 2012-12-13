(ns balabit.logstore.codec.timestamp

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (java.nio ByteBuffer))
  (:use [balabit.blobbity]))

;; ### Timestamp records
;;
;; Timestamp records are used to apply cryptographically secure
;; timestamps to previous records within a LogStore.
;;
;; Right now, the library does not process these further than
;; extracting them as binary data.
;;
(defmethod decode-frame :logstore/record.timestamp
  [#^ByteBuffer buffer _ header]

  (let [timestamp (decode-frame buffer :prefixed :slice :uint32)]
    (decode-frame buffer :skip (.capacity timestamp))
    (assoc header :timestamp timestamp)))
