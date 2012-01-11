(ns balabit.logstore.core
  "Core functions to read syslog-ng PE's LogStore files."
  (:import (java.nio ByteBuffer)
           (java.io FileInputStream InputStream))
  (:use [slingshot.slingshot :only [throw+]])
  (:require [balabit.logstore.errors :as errors]))

(def READ_ONLY #^{:private true}
  (java.nio.channels.FileChannel$MapMode/READ_ONLY))

(defrecord LSTCryptoHeader [algo_hash, algo_crypt, file_mac, der])
(defrecord LSTFileHeader [magic, length, flags, last_chunk, last_rec, last_chunk_end,
                          crypto])

(defrecord LSTFile [header handle])

(defn- lst-read-bytes
  "Read a given amount of bytes from a ByteBuffer, and return them
as a byte array."
  [handle length]
  (let [buffer (make-array (. Byte TYPE) length)]
    (.get handle buffer 0 length)
    buffer))

(defn- lst-read-block
  "Read a length-prefixed block from a LogStore ByteBuffer, and
return the result as a byte array."
  [handle]

  (let [length (.getInt handle)]
    (lst-read-bytes handle length)))

(defn- lst-crypto-header-read
  "Read the crypto header part of a LogStore file header, and return
an LSTCryptoHeader record."
  [handle]

  (LSTCryptoHeader. (String. (lst-read-block handle)) ; algo.hash
                    (String. (lst-read-block handle)) ; algo.crypt
                    (lst-read-block handle) ; file_mac
                    (lst-read-block handle) ; der
                    ))

(defn- lst-file-header-read
  "Read the file header of a LogStore ByteBuffer.
Returns an LSTFileHeader instance."
  [handle]

  (let [magic (String. (lst-read-bytes handle 4))]
    (if (not (or
              (= magic "LST3")
              (= magic "LST4")))
      (throw+ (errors/invalid-file :magic "File is not valid LST3/LST4")))
    (LSTFileHeader. magic  ; magic
                    (.getInt handle)  ; length
                    (.getInt handle)  ; flags
                    (.getInt handle)  ; last_chunk
                    (.getInt handle)  ; last_rec
                    (.getLong handle) ; last_chunk_end
                    (do (.position handle 136) (lst-crypto-header-read handle)))))

(defn lst-open
  "Open a LogStore file. Returns an LSTFile, or throws an exception on error."
  [filename]

  (let [channel (.getChannel (FileInputStream. filename))
        handle (.map channel READ_ONLY 0 (.size channel))
        header (lst-file-header-read handle)]
    (LSTFile. header handle)))

(defn lst-file-map
  "Map the records within a LogStore file, for optionally faster record retrieval."
  [handle]

  nil)

(defn lst-get-record
  "Get the nth record from a LogStore ByteBuffer."
  [handle record-index & map]
  nil)
