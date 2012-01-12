(ns balabit.logstore.test.core
  (:require [balabit.logstore.core :as lst])
  (:use [midje.sweet])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(fact "about valid logstores being openable"
      (lst/open "resources/loggen.store") =not=> nil)

(defn open-invalid []
  (try+
   (lst/open "project.clj")
   (catch [:type :balabit.logstore.errors/invalid-file] {:keys [message]}
     true)))

(defn open-non-existant []
  (try+
   (lst/open "does-not-exist.store")
   (catch java.lang.RuntimeException e
     (= (class (.getCause e)) java.io.FileNotFoundException))))

(fact "about an invalid logstore throwing an exception"
      (open-invalid) => true)

(fact "about a non-existing logstore throwing an exception"
      (open-non-existant) => true)

(def loggen-store (lst/open "resources/loggen.store"))

(facts "about logstore meta-data"
       (:magic (:header loggen-store)) => "LST4"
       (:length (:header loggen-store)) => 183
       (:algo_hash (:crypto (:header loggen-store))) => "SHA1"
       (:algo_crypt (:crypto (:header loggen-store))) => "AES-128-CBC")

(fact "about the number of records in the LogStore"
      (lst/count loggen-store) => 9)

(fact "about a record read from a LogStore"
       (lst/nth loggen-store 1) =not=> nil)

(def loggen-store-record (lst/nth loggen-store 1))

(facts "about a record read from a LogStore"
       (:offset (:header loggen-store-record)) => 13878
       (:size (:header loggen-store-record)) => 14079
       (:type (:header loggen-store-record)) => :chunk
       (:flags (:header loggen-store-record)) => [:compressed]
       (.limit (:data loggen-store-record)) => 13979
       (:first_msgid loggen-store-record) => 4162
       (:last_msgid loggen-store-record) => 8322
       (:chunk_id loggen-store-record) => 1
       (:xfrm_offset loggen-store-record) => 0
       (:flags loggen-store-record) => [:hash])
