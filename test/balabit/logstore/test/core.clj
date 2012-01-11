(ns balabit.logstore.test.core
  (:require [balabit.logstore.core :as lst])
  (:use [midje.sweet])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(fact "about valid logstores being openable"
      (lst/lst-open "resources/loggen.store") =not=> nil)

(defn open-invalid []
  (try+
   (lst/lst-open "project.clj")
   (catch [:type :balabit.logstore.errors/invalid-file] {:keys [message]}
     true)))

(defn open-non-existant []
  (try+
   (lst/lst-open "does-not-exist.store")
   (catch java.lang.RuntimeException e
     (= (class (.getCause e)) java.io.FileNotFoundException))))

(fact "about an invalid logstore throwing an exception"
      (open-invalid) => true)

(fact "about a non-existing logstore throwing an exception"
      (open-non-existant) => true)

(def loggen-store (lst/lst-open "resources/loggen.store"))

(facts "about logstore meta-data"
       (:magic (:header loggen-store)) => "LST4"
       (:length (:header loggen-store)) => 183
       (:algo_hash (:crypto (:header loggen-store))) => "SHA1"
       (:algo_crypt (:crypto (:header loggen-store))) => "AES-128-CBC")
