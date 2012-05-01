(ns balabit.logstore.core.record.timestamp
  "LogStore timestamp record functions.

   These are not meant to be used directly, but through
   `balabit.logstore.record`."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "GNU General Public License - v3"
              :url "http://www.gnu.org/licenses/gpl.txt"}}

  (:require [balabit.logstore.core.record.common :as lgs-rec-common]
            [gloss.core]
            [gloss.io])
  (:import (java.nio ByteBuffer))
  (:use [balabit.logstore.core.utils]))

;; ## Records
;; - - - - -

;; A Timestamp record is pretty simple: a chunk-id and a binary
;; timestamp. At least, for now. At a later point, the timestamp may
;; be parsed further, just like chunk messages are processed now.
(defrecord LSTRecordTimestamp [header
                               chunk-id
                               timestamp])

;; ## Codecs
;; - - - - -

;; The timestamp records are an interesting beast: they're always 4096
;; bytes long (6 bytes of which is the generic *record header*),
;; contains a `chunk-id`, and a length-prefixed binary timestamp. The
;; rest of the record is padding.
;;
;; To read this, we use a header, where the header->body function
;; assembles the rest of the codec, calculating the length of the
;; timestamp and the padding in the process.
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

;; ## Published methods
;; - - - - - - - - - -

;; Extends the `read-record-data` multi-method in
;; `balabit.logstore.core.record.common` for the `:timestamp` record
;; type.
;;
;; It does no post-processing of the timestamp (yet).
(defmethod lgs-rec-common/read-record-data :timestamp
  [header #^ByteBuffer handle]

  (let [original-pos (.position handle)
        ts (gloss.io/decode record-timestamp
                            (slice-n-dice handle 4090))]
    (.position handle (+ original-pos (:size header) -6))
    (LSTRecordTimestamp. header
                         (:chunk-id ts)
                         (-> ts :timestamp :data first))))
