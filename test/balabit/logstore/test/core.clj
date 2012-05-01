(ns balabit.logstore.test.core
  (:require [balabit.logstore.core.file :as lst])
  (:import (java.net URL)
           (java.io File FileInputStream)
           (java.nio ByteBuffer))
  (:use [midje.sweet]
        [balabit.logstore.test.utils]
        [slingshot.slingshot :only [try+]]
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
       (catch+ [:context :type]
               (lst/open 12)) => #"Can't open .* as a LogStore file"
       (catch+ [:context :type]
               (lst/open nil)) => #"Can't open .* as a LogStore file")
          
(fact "about an invalid logstore throwing an exception"
      (catch+ [:type :invalid-file]
              (lst/open "project.clj")) => #"File is not valid")

(fact "about a non-existing logstore throwing an exception"
      (try+
       (lst/open "does-not-exist.store")
       (catch Exception e
         (is-exception? e java.io.FileNotFoundException))) => true)
