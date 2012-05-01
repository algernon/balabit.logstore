(ns balabit.logstore.test.core
  (:require [balabit.logstore.core.file :as lst])
  (:import (java.net URL)
           (java.io File FileInputStream)
           (java.nio ByteBuffer))
  (:use [midje.sweet]
        [slingshot.slingshot :only [throw+ try+]]
        [clojure.java.io :only [resource]]))

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

(facts "about not supported types not being openable"
       (try+
        (lst/open 12)
        (catch [:context :type] {:keys [message]}
          message)) => #"Can't open .* as a LogStore file"

       (try+
        (lst/open nil)
        (catch [:context :type] {:keys [message]}
          message)) => #"Can't open .* as a LogStore file")
          
(defn is-exception?
  [e expected]

  (if (.getCause e)
    (= (class (.getCause e)) expected)
    (= (class e) expected)))

(fact "about an invalid logstore throwing an exception"
      (try+
       (lst/open "project.clj")
       (catch [:type :invalid-file] {:keys [message]}
         message)) => #"File is not valid")

(fact "about a non-existing logstore throwing an exception"
      (try+
       (lst/open "does-not-exist.store")
       (catch Exception e
         (is-exception? e java.io.FileNotFoundException))) => true)
