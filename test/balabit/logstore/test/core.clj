(ns balabit.logstore.test.core
  (:require [balabit.logstore.core.file :as lst])
  (:import (java.net URL)
           (java.io File FileInputStream)
           (java.nio ByteBuffer))
  (:use [midje.sweet])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:use [clojure.java.io :only [resource]]))

(def store (resource "logstores/loggen.compressed.store"))

(fact "about valid logstores being openable"
      (lst/open (.getFile store)) =not=> nil)

(fact "about URLs being openable"
      (lst/open store) =not=> nil)

(fact "about File instances being openable"
      (lst/open (File. (.getFile store))) =not=> nil)

(fact "about FileInputStream instances being openable"
      (lst/open (FileInputStream. (File. (.getFile store)))) =not=> nil)

(let [stream (FileInputStream. (File. (.getFile store)))
      READ_ONLY (java.nio.channels.FileChannel$MapMode/READ_ONLY)
      channel (.getChannel stream)
      buffer (.map channel READ_ONLY 0 (.size channel))]
  (fact "about ByteBuffer instances being openable"
        (lst/open buffer) =not=> nil))

(defn open-invalid-type
  [p]

  (try+
   (lst/open p)
   (catch [:context :type] {:keys [message]}
       true)))

(facts "about not supported types not being openable"
       (open-invalid-type 12) => true
       (open-invalid-type nil) => true)

(defn open-invalid []
  (try+
   (lst/open "project.clj")
   (catch [:type :invalid-file] {:keys [message]}
     true)))

(defn open-non-existant []
  (try+
   (lst/open "does-not-exist.store")
   (catch Exception e
     (or
      (= (class (.getCause e)) java.io.FileNotFoundException)
      (= (class e) java.io.FileNotFoundException)))))

(fact "about an invalid logstore throwing an exception"
      (open-invalid) => true)

(fact "about a non-existing logstore throwing an exception"
      (open-non-existant) => true)
