(ns balabit.logstore
  "A convenient API for reading syslog-ng PE LogStore files."

  (:require [balabit.logstore.core :as lgs])
  (:import balabit.logstore.core.LSTFile))

(declare ^:dynamic ^LSTFile *logstore*)

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
