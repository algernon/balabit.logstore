(ns balabit.logstore.sweet
  "## Entry point of the LogStore reader API"
  
  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}
  
  (:import (java.io FileInputStream))
  (:use [balabit.logstore.codec]))

;; The library is built upon a very simple idea: that is, to lazily
;; load a LogStore, process and parse it into a simple, logically
;; structured map. Once it is in a map, it becomes easy to work with
;; it using nothing more than standard Clojure functions.
;;
;; To make it even easier to do this, a few helper functions are
;; provided:
;;

(defn from-file
  "Load a LogStore file, by mapping into memory and parsing it. Takes
  a filename, and returns a map of the LogStore."

  [fn]
  
  (let [channel (.getChannel (FileInputStream. fn))]
    (decode-logstore (.map channel
                           (java.nio.channels.FileChannel$MapMode/READ_ONLY)
                           0 (.size channel)))))

(defn messages
  "Return a lazy list of all the messages from within a LogStore."
  
  [logstore]

  (mapcat #(get % :messages) (:records logstore)))
