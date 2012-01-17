(ns balabit.logstore.core.record.chunk
  "LogStore chunk record functions."

  (:require [balabit.logstore.core.record.common :as lgs-rec-common])
  (:import (org.joda.time DateTime)
           (java.io DataInputStream)
           (java.util.zip InflaterInputStream Inflater))
  (:use balabit.logstore.core.utils))

(defrecord LSTRecordChunk [header
                           start-time end-time
                           first-msgid last-msgid
                           chunk-id
                           xfrm-offset

                           messages
                           flags
                           file-mac
                           chunk-mac
                           ])

(defn- translate-timestamp
  "Translate a timestamp consisting of a 64bit seconds part, and a
  32bit microsecond part into a single value, into a DateTime
  object. It does loose a little bit of precision (it can only handle
  millis)."
  [secs usecs]

  (DateTime. (+ (* secs 1000) (quot usecs 1000))))

;;
;; # Chunk format handling
;;

(def chunk-flag-bitmap #^{:private true}
  [:hmac
   :hash])

(defmulti chunk-decompress
  "Decompress a chunk, if so need be."
  (fn [record-header data-size data] (flag-set? record-header :compressed)))

(defmethod chunk-decompress :default
  [record-header data-size data] data)

(defmethod chunk-decompress true
  [record-header data-size data]
  (InflaterInputStream. data (Inflater.) data-size))

(defmulti chunk-data-split
  "Split unserialized data into chunks"
  (fn [record-header msgcnt data] (flag-set? record-header :serialized)))

(defmethod chunk-data-split :serialized
  [record-header msgcnt data] data)

(defn- chunk-data-read-line
  "Read a length-prefixed log line from a DataInputStream."
  [stream]

  (try
    (let [len (.readInt stream)]
      (let [buffer (byte-array len)]
        (.read stream buffer)
        buffer))
    (catch Exception _
      nil)))

(defmethod chunk-data-split :default
  [record-header msgcnt data]

  (let [stream (DataInputStream. data)]
    (loop [messages []
           remaining msgcnt]
      (let [line (chunk-data-read-line stream)]
        (if (< remaining 0)
          messages
          (recur (conj messages line) (dec remaining)))))))

(defn- chunk-data-stringify
  "Cast all members of data to Strings."
  [data]

  (map #(String. %) data))

(defn- chunk-decode
  [header msgcnt data data-size]
  ((comp chunk-data-stringify
         (partial chunk-data-split header msgcnt)
         (partial chunk-decompress header data-size)
         bb-buffer-stream)
   data))

(gloss.core/defcodec- record-chunk-header
  (gloss.core/ordered-map
   :start-time :uint64
   :start-time-usec :uint32
   :end-time :uint64
   :end-time-usec :uint32
   :first-msgid :uint32
   :last-msgid :uint32
   :chunk-id :uint32
   :xfrm-offset :uint64
   :tail-offset :uint32))

(defmacro record-chunk-assemble-trail
  [header]
  
  `(gloss.core/compile-frame
    (gloss.core/ordered-map
     :chunk-data (gloss.core/finite-block (- (:tail-offset ~header) 54)),
     :flags :uint32,
     :file-mac (gloss.core/finite-block :uint32)
     :chunk-hmac (gloss.core/finite-block :uint32)
     )))

;; Reads :chunk type LogStore records, and parses the sub-header, and
;; will handle data demungling too, at a later time.
(defmethod lgs-rec-common/read-record-data :chunk
  [header handle]

  (let [original-pos (.position handle)
        chunk-header (gloss.io/decode record-chunk-header
                                      (slice-n-dice handle 48))
        raw-data (slice-n-dice handle (+ original-pos 48)
                               (- (:size header) 54))
        chunk-trail (gloss.io/decode
                     (record-chunk-assemble-trail chunk-header)
                     raw-data)]
    (.position handle (+ original-pos (:size header) -6))
    (LSTRecordChunk. header
                     (translate-timestamp (long (:start-time chunk-header))
                                          (:start-time-usec chunk-header))
                     (translate-timestamp (long (:end-time chunk-header))
                                          (:end-time-usec chunk-header))
                     (:first-msgid chunk-header) (:last-msgid chunk-header)
                     (:chunk-id chunk-header)
                     (:xfrm-offset chunk-header)
                     (chunk-decode header (- (:last-msgid chunk-header)
                                             (:first-msgid chunk-header))
                                   (first (:chunk-data chunk-trail))
                                   (- (:tail-offset chunk-header) 54))
                     (resolve-flags (:flags chunk-trail) chunk-flag-bitmap)
                     (:file-mac chunk-trail)
                     (:chunk-hmac chunk-trail))))

