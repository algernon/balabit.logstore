(ns balabit.logstore.test.core
  (:require [balabit.logstore.core.file :as lst])
  (:use [midje.sweet])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:use [clojure.java.io :only [resource]]))

(fact "about valid logstores being openable"
      (lst/open (resource "logstores/loggen.compressed.store")) =not=> nil)

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
