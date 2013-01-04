(ns balabit.logstore.crypto
  "## Crypto & digest helper functions"
  
  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (java.nio ByteBuffer)
           (java.security MessageDigest)))

(defn- find-algo
  "Translate a given algorithm from OpenSSL/LogStore nomenclature to
  java.security.MessageDigest scheme.

  Takes a keyword, returns a string."

  [algo]

  (let [a (name algo)]
    (if (and (.startsWith a "SHA")
             (not (.startsWith a "SHA-")))
      (str "SHA-" (subs a 3))
      a)))

(defn digest
  "Return the message digest of a ByteBuffer-worth of data, using the
  specified digest algorithm."

  [algo #^ByteBuffer data]

  (let [buffer (byte-array (.limit data))]
    (.get data buffer)
    (.digest (MessageDigest/getInstance (find-algo algo)) buffer )))
