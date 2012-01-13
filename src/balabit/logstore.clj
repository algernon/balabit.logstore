;; ## syslog-ng PE LogStore reader

(ns balabit.logstore
  (:require [balabit.logstore.core.file :as lgs]
            [balabit.logstore.core.record :as lgs-record])
  (:use balabit.logstore.impl.logstore)
  (:import balabit.logstore.core.file.LSTFile)
  (:import balabit.logstore.core.record.LSTRecord))

(declare ^:dynamic ^LSTFile *logstore*)
(declare ^:dynamic ^LSTRecord *logstore-record*)

;;
;; LogStore convenience macros
;;

(defmacro with-logstore
  "Do stuff with a LogStore. The macro keeps the LogStore file opened,
  and binds the *logstore* symbol to the opened file."
  [filename & body]
  `(binding [*logstore* (lgs/open ~filename)]
     (do ~@body)))

(defmacro logstore-header
  "Returns the header of the currently opened LogStore file."
  [& fields]
  `(reduce get (:header *logstore*) [~@fields]))

(defmacro logstore-records
  "Returns the record headers in an opened LogStore file"
  []
  `(:record-map *logstore*))

(defmacro logstore-nth
  "Returns the Nth record from an opened LogStore file"
  [index]
  `(lgs/nth *logstore* ~index))

;;
;; LogStore record convenience macros
;;

(defmacro with-logstore-record
  "Do stuff with a specific LogStore record"
  [index & body]
  `(binding [*logstore-record* (logstore-nth ~index)]
     (do ~@body)))

(defmacro logstore-record
  "Returns the current LogStore record, or optionally, a field of
  it."
  [& fields]
  `(reduce get *logstore-record* [~@fields]))

(make-record-flag-accessors compressed encrypted broken serialized)
