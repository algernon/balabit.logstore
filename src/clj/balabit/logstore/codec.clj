(ns balabit.logstore.codec
  "## High level LogStore codec definitions"

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (java.nio ByteBuffer))
  (:use [balabit.blobbity]
        [balabit.logstore.utils]
        [balabit.logstore.exceptions]
        [balabit.logstore.codec.verify]
        [balabit.logstore.codec.common]
        [balabit.logstore.codec.chunk]
        [balabit.logstore.codec.timestamp]
        [balabit.logstore.codec.xfrm-info]))

;; On the top level, a LogStore file consists of a file header,
;; followed by a number of records (where records can be of varying
;; types). So all we need, are two methods: one to parse the header,
;; and another to parse any kind of record. Then we only need to bind
;; them together, and we have a map of the LogStore.


;; ### The file header
;;
;; The file header is fairly simple: It starts with four magic bytes,
;; which identify which version of the LogStore spec the file is
;; in. This library supports `LST3` and `LST4` files only.
;;
;; Following the magic, we have the length of the header (a 32-bit
;; integer), followed by a set of flags (also 32-bit integer), which
;; are currently unused. Past the flags, the header contains
;; information about the id of the last record within the LogStore,
;; the id of the last message within it (both 32-bit integers), and an
;; offset to the end of the last record (a 64-bit integer).
;;
;; All of this information is useful for the parser, but post parsing,
;; not so much. Therefore they do not appear in the map emitted.
;;
;; For future enhancements, 108 bytes were reserved past this point,
;; which the reader skips.
;;
;; The rest of the file header is about encryption and hashing: we
;; have two strings describing the crypto hash and encrypt methods
;; (both strings are prefixed by a 32-bit length). Following these, we
;; have the MAC of the file, and a DER.
;;
(defmethod decode-frame :logstore/file.header
  [#^ByteBuffer buffer _]

  (->
   (decode-blob buffer [:magic [:string 4],
                        :length :uint32,
                        :flags :uint32,
                        :last [:struct [:record-id :uint32,
                                        :message-id :uint32,
                                        :record-end-offset :uint64]],
                        :skip 108,
                        :crypto [:struct [:algo [:struct [:hash [:prefixed :string :uint32],
                                                          :crypt [:prefixed :string :uint32]]],
                                          :file-mac :logstore/common.mac,
                                          :der [:prefixed :slice :uint32]]]])
   (verify-frame :logstore/file.header)
   (dissoc :magic :length :flags :last)))

;; To be able to return meaningful exceptions on error, the header
;; needs to be verified. This means that the file magic needs to be
;; correct, and no flags are to be set.
(defmethod verify-frame :logstore/file.header
  [file-header type]

  (-> file-header
      (assert-format {:source type
                      :message "Invalid magic"}
                     (or (= (:magic file-header) "LST4")
                         (= (:magic file-header) "LST3")))

      (assert-format {:source type
                      :message "Invalid flags"}
                     (zero? (:flags file-header)))))

;; ### Anatomy of a LogStore record
;;
;; Each record in a LogStore starts with a header, where the first
;; part of that header (see the
;; [logstore/record.common-header][lgs/ch] documentation) is common to
;; all types of headers.
;;
;; [lgs/ch]: #balabit.logstore.codec.common
;;
;; Reading a record in an uniform way is as simple as grabbing the
;; header, and dispatching to the appropriate decoder for the type,
;; and merging the results.
;;
;; From the resulting map, we remove some unused fields that are only
;; useful during parsing. The returned map will have at least a
;; `:type` member, that tells us the type of the record, and it
;; optionally has a `:flags` field, if any flags are set. Not all
;; types of records have flags defined, those that do not, will not
;; have the flag member in the map.
;;
;; Other members will be present too, depending on the type of the
;; record.
;;
;; The recognised record types are:
;;
;; * [`:chunk`][lgs/chunk]: A chunk of messages, contains log messages and
;;   some meta-data about them.
;; * [`:timestamp`][lgs/timestamp]: A timestamp for a number of previous
;;   chunks.
;;
;; [lgs/chunk]: #balabit.logstore.codec.chunk
;; [lgs/timestamp]: #balabit.logstore.codec.timestamp
;;
;; Any other type of tag will cause an exception.
(defmethod decode-frame :logstore/record
  [#^ByteBuffer buffer _]

  (let [header (decode-frame buffer :logstore/record.common-header)
        maybe-dissoc (fn [coll field]
                       (if (empty? (get coll field))
                         (dissoc coll field)
                         coll))]
    (-> header
        (merge (decode-frame buffer (keyword (str "logstore/record."
                                                  (name (:type header)))) header))
        (dissoc :offsets :size)
        (maybe-dissoc :flags))))

;; ### Putting it all together
;;

(defn decode-logstore
  "Given a LogStore in a buffer, decode it, and return a map of
  it. The returned map will contain the file header, and a `:records`
  key, a lazy list of records available."

  [#^ByteBuffer buffer]

  (assoc (decode-frame buffer :logstore/file.header)
    :records (decode-blob-array buffer :logstore/record)))
