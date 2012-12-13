(ns balabit.logstore.codec.common

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (java.nio ByteBuffer))
  (:use [balabit.blobbity]
        [balabit.logstore.utils]))

;; ### The common record header
;;
;; Each record has a common header, that starts by a 32-bit `:size`,
;; followed by a 8-bit `:type`, and 8 bits of `:flags`.
;;
;; For the sake of clarity, both types and flags are resolved to
;; keywords.
;;
;; There can be multiple flags set, so in the output, `:flags` will
;; appear as a vector of keywords.
;;
;; Known flags are:
;;
;; * `:compressed`: The record itself is gzip-compressed. This flag is
;;   only valid for `:chunk`-type records.
;; * `:encrypted`: The record is encrypted. This flag is only valid
;;   for `:chunk`-type records.
;; * `:serialized`: The record contains serialized messages, and as
;;   such, is only valid for `:chunk`-type records.
;; * `:broken`: The record is broken, or not fully written yet. These
;;   should not appear in properly closed LogStores.
;;
(defmethod decode-frame :logstore/record.common-header
  [#^ByteBuffer buffer _]

  (-> (decode-blob buffer [:size :uint32
                           :type :byte
                           :flags :byte])
      (update-in [:type]
                 (partial nth [:invalid :xfrm-info :chunk :timestamp]))

      (update-in [:flags]
                 (partial resolve-flags [:compressed
                                         :encrypted
                                         :broken
                                         :serialized]))))

