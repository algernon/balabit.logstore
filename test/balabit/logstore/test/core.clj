(ns balabit.logstore.test.core
  (:require [balabit.logstore.core.file :as lst])
  (:use [midje.sweet])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(fact "about valid logstores being openable"
      (lst/open "resources/logstores/loggen/loggen.compressed.store") =not=> nil)

(defn open-invalid []
  (try+
   (lst/open "project.clj")
   (catch [:type :invalid-file] {:keys [message]}
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
