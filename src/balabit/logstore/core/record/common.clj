(ns balabit.logstore.core.record.common
  "Common functions and definitions for all record types.

   These are in a separate namespace to avoid a circular dependency
   between `balabit.logstore.core.record` and
   `balabit.logstore.core.record.*`."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license "All rights reserved"}

  (:require [gloss.core]
            [gloss.io]))

;; ## Records
;; - - - - -

;; The generic record header contains all the information shared
;; between all record types, including the offset at which the header
;; was found in the LogStore.
(defrecord LSTRecordHeader [offset size type flags])

;; ## Codecs
;; - - - - -

;; Codec for the shared record header.
(gloss.core/defcodec record-common-header
  (gloss.core/ordered-map
   :size :uint32
   :type :byte
   :flags :byte))

;; ## Published functions
;; - - - - - - - - - - -

(defmulti read-record-data
  "Read a given type of record from a LogStore ByteBuffer. The record
   header is already parsed, and available as record-header, the buffer
   behind the handle is positioned right after the record header.

   The various record-type reader implementations are supposed to
   implement the different versions of this function."
  (fn [record-header handle] (:type record-header)))
