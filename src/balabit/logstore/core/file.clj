(ns balabit.logstore.core.file
  "Core functions to read syslog-ng PE's LogStore files."
  (:import (java.io FileInputStream))
  (:use [slingshot.slingshot :only [throw+]]
        [balabit.logstore.core.utils])
  (:refer-clojure :exclude [open count nth])
  (:require [balabit.logstore.core.errors :as errors]
            [balabit.logstore.core.record :as lst-record]
            [gloss.core]
            [gloss.io]))

(def READ_ONLY #^{:private true}
  (java.nio.channels.FileChannel$MapMode/READ_ONLY))

(defrecord LSTCryptoHeader [algo-hash, algo-crypt, file-mac, der])
(defrecord LSTFileHeader [magic, flags,
                          last-block-id, last-chunk-id, last-block-end-offset,
                          crypto])

(defrecord LSTFile [header handle record-map])

(gloss.core/defcodec- file-magic-codec
  (gloss.core/ordered-map
   :magic (gloss.core/string :utf-8 :length 4),
   :length :uint32))

(gloss.core/defcodec- file-header-codec
  (gloss.core/ordered-map
   :flags :uint32,
   :last-block-id :uint32,
   :last-chunk-id :uint32,
   :last-block-end-offset :uint64,
   :padding (gloss.core/finite-block 108),

   :algo-hash (gloss.core/finite-frame :uint32 (gloss.core/string :utf-8)),
   :algo-crypt (gloss.core/finite-frame :uint32 (gloss.core/string :utf-8)),
   :file-mac (gloss.core/finite-block :uint32),
   :der (gloss.core/finite-block :uint32)))

(defn- lst-file-header-read
  "Read the file header of a LogStore ByteBuffer.
Returns an LSTFileHeader instance."
  [handle]

  (let [file-magic (gloss.io/decode file-magic-codec (slice-n-dice handle 8))]
    (if (not (or
              (= (:magic file-magic) "LST3")
              (= (:magic file-magic) "LST4")))
      (throw+ (errors/invalid-file :magic "File is not valid LST3/LST4")))
    (let [hdr (gloss.io/decode file-header-codec
                               (slice-n-dice handle 8
                                             (- (:length file-magic) 4)))]
      (.position handle (+ 4 (:length file-magic)))
      (LSTFileHeader. (:magic file-magic)
                      (:flags hdr)
                      (:last-block-id hdr)
                      (:last-chunk-id hdr)
                      (:last-block-end-offset hdr)
                      (LSTCryptoHeader.
                         (:algo-hash hdr)
                         (:algo-crypt hdr)
                         (:file-mac hdr)
                         (:der hdr))))))

(defn- lst-file-map-record
  "Read a record from a LogStore ByteBuffer, and seek to its end."
  [lst]

  (let [handle (:handle lst)
        record-header (lst-record/read-header lst)]
    (.position handle (+ (:offset record-header) (:size record-header)))
    record-header))

(defn- lst-file-map
  "Map all records from a LogStore ByteBuffer."
  [lst]

  (loop [result []]
    (if (< (-> lst :handle .position) (-> lst :handle .limit))
      (let [rec (lst-file-map-record lst)]
        (recur (conj result rec)))
      result)))

(defn open
  "Open a LogStore file. Returns an LSTFile, or throws an exception on error."
  [filename]

  (let [channel (.getChannel (FileInputStream. filename))
        handle (.map channel READ_ONLY 0 (.size channel))
        header (lst-file-header-read handle)]
    (LSTFile. header handle (lst-file-map (LSTFile. header handle {})))))

(defn count
  "Count the elements in a LogStore file."
  [lst]
  (clojure.core/count (:record-map lst)))

(defn nth
  "Get the nth record from a LogStore."
  [lst index]

  (let [record-header (clojure.core/nth (:record-map lst) index)]
    (.position (:handle lst) (:offset record-header))
    (lst-record/read lst)))
