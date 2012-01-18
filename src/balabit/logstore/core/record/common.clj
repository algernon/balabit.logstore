(ns balabit.logstore.core.record.common
  "Common functions and definitions for all record types."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license "All rights reserved"}

  (:require [gloss.core]
            [gloss.io]))

(defrecord LSTRecordHeader [offset size type flags])

(gloss.core/defcodec record-common-header
  (gloss.core/ordered-map
   :size :uint32
   :type :byte
   :flags :byte))

(defrecord LSTRecord [header data])

(defmulti read-record-data
  "Read a given type of record from a LogStore ByteBuffer. The record
header is already parsed, and available as record-header, the buffer
behind the handle is positioned right after the record header."
  (fn [record-header handle] (:type record-header)))
