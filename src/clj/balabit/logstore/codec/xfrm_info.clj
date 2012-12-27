(ns balabit.logstore.codec.xfrm-info

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (java.nio ByteBuffer))
  (:use [balabit.blobbity]
        [balabit.logstore.exceptions]
        [balabit.logstore.codec.verify]))

;; ### XFRM info records
;;
;; XFRM info records contain the master key, encrypted with the public
;; part of the X509 cert, which is stored in the [file header][fhdr].
;;
;; The library simply extracts the encrypted master key, and does no
;; more than that at this time.
;;
;; [fhdr]: #balabit.logstore.codec
;;
(defmethod decode-frame :logstore/record.xfrm-info
  [#^ByteBuffer buffer _ header & _]

  (let [xfrm-info (decode-frame buffer :prefixed :slice :uint32)]
    (assoc header :xfrm-info xfrm-info)))
