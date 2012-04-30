(ns balabit.logstore.core.file
  "Core functions to read syslog-ng PE's LogStore files."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "GNU General Public License - v3"
              :url "http://www.gnu.org/licenses/gpl.txt"}}

  (:import (java.io FileInputStream)
           (java.nio ByteBuffer))
  (:use [slingshot.slingshot :only [throw+]]
        [balabit.logstore.core.utils])
  (:refer-clojure :exclude [open count nth])
  (:require [balabit.logstore.core.errors :as errors]
            [balabit.logstore.core.record :as lst-record]
            [gloss.core]
            [gloss.io]))

;; A quick hack to make the READ_ONLY MapMode accessible.
(def READ_ONLY #^{:private true}
  (java.nio.channels.FileChannel$MapMode/READ_ONLY))

;; ## Header structures
;; - - - - - - - - - -

;; The crypto header, a sub-header of the LogStore file header.
(defrecord LSTCryptoHeader [algo-hash, algo-crypt, file-mac, der])

;; The full LogStore file header, with all interesting properties
;; stored for easy access.
(defrecord LSTFileHeader [magic, flags,
                          last-block-id, last-chunk-id, last-block-end-offset,
                          crypto])

;; This record is used to describe a LogStore file. It contains the
;; header, an mmapped ByteBuffer, and a record map, which is a vector
;; of all the records (blocks) contained within the logstore.
(defrecord LSTFile [header handle record-map])

;; ## Codecs
;; - - - - -

;; Gloss codec to decode the magic part of a LogStore file. It is
;; separate from the rest, because we want to examine the magic as
;; soon as possible, and bail out with an appropriate exception in
;; case the magic is bad.
;;
;; The 'magic header' consists of a four-byte char sequence, that
;; tells us the version of the logstore (we support LST3 and LST4, see
;; below).
(gloss.core/defcodec- file-magic-codec
  (gloss.core/ordered-map
   :magic (gloss.core/string :utf-8 :length 4),
   :length :uint32))

;; Codec for the rest of the file header, with the crypto sub-header
;; included. The contents should be self-explanatory.
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

;;
;; ## Helper functions
;; - - - - - - - - - -
;;
;; All of these helper functions that deal with parsing the LogStore
;; can throw any of the exceptions that the underlying libraries
;; throw. It is not handled on this library's level (yet?), those
;; exceptions will bubble up to the application as-is.
;;
;; Whenever a function can throw something specific to this library,
;; it will be noted in the appropriate section of the documentation.
;;

(defn- lst-file-header-read
  "Read the file header of a LogStore ByteBuffer, and return an
  `LSTFileHeader` instance.

  Throws an `invalid-file` exception if the magic is invalid."
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
  "Read a record from a LogStore ByteBuffer, and seek to its
  end. Returns an `LSTRecordHeader` instance."
  [lst]

  (let [handle (:handle lst)
        record-header (lst-record/read-header lst)]
    (.position handle (+ (:offset record-header) (:size record-header)))
    record-header))

(defn- lst-file-map
  "Map all records from a LogStore ByteBuffer. This is done by reading
  through the whole file until no bytes are left to read.

  Returns a vector of `LSTRecordHeader` instances."
  [lst]

  (loop [result []]
    (if (< (-> lst :handle .position) (-> lst :handle .limit))
      (let [rec (lst-file-map-record lst)]
        (recur (conj result rec)))
      result)))

;;
;; ## Published functions
;; - - - - - - - - - - -
;;
;; The functions below are published towards the rest of the library,
;; and can be used to do basic work on LogStore files.
;;

(defmulti open
  "Open a LogStore file, and map its records. Returns an `LSTFile`
  instance, or throws an exception on error.

  Depending on whether the LogStore to open is local (or already
  opened by other means) or remote, it is either mmapped, or its whole
  contents are read into memory first."

  class)

;; In the simplest case, we have a ByteBuffer, which we can directly
;; use to construct an LSTFile instance.
(defmethod open java.nio.ByteBuffer
  [buffer]

  (let [header (lst-file-header-read buffer)]
    (LSTFile. header buffer (lst-file-map (LSTFile. header buffer {})))))

;; But in case we have a FileInputStream, we have to mmap it first,
;; from which we get a ByteBuffer, which is handled already.
(defmethod open java.io.FileInputStream
  [stream]

  (let [channel (.getChannel stream)
        buffer (.map channel READ_ONLY 0 (.size channel))]
    (open buffer)))

;; Opening a File instance is also easy: just wrap it in a
;; FileInputStream, and we're done, that case is already handled!
(defmethod open java.io.File
  [file]

  (open (FileInputStream. file)))

;; If we have a FilterInputStream, we have no other choice, but to
;; read it into memory in full, and wrap it in a ByteBuffer, so the
;; basic open can handle it.
(defmethod open java.io.FilterInputStream
  [connection]

  (let [buffer (byte-array (.available connection))]
    (.read connection buffer)
    (open (ByteBuffer/wrap buffer))))

;; We can also receive an URL instance, in which case we grab its
;; contents, which is likely to be either a ByteBuffer, or a
;; derivative of FilterInputStream - both of which can be opened
;; already.
(defmethod open java.net.URL
  [url]

  (open (.getContent url)))

;; The tricky part is when we get a string: that can be either an URL,
;; or a simple filename. We do some dirty hackery here: first we try
;; to create an URL instance out of the string, and if that fails, we
;; wrap it in a FileInputStream.
;;
;; If either of those succeed, we have an open method implemented for
;; either type already.
(defmethod open java.lang.String
  [filename]

  (try
    (let [url (java.net.URL. filename)]
      (open url))
    (catch Exception e
      (open (FileInputStream. filename)))))

;; Trying to open anything else but the cases defined above, shall
;; throw an exception.
(defmethod open :default
  [p]

  (throw+ (errors/invalid-file
           :type (str "Can't open " (type p) " as a LogStore file"))))

(defn count
  "Count the records in a LogStore file."
  [lst]
  (clojure.core/count (:record-map lst)))

(defn nth
  "Get the nth record from a LogStore. The returned value's type
  depends on the type of the record, it can be any of `LSTRecordChunk`
  or `LSTRecordTimestamp`."
  [lst index]

  (let [record-header (clojure.core/nth (:record-map lst) index)]
    (.position (:handle lst) (:offset record-header))
    (lst-record/read lst)))
