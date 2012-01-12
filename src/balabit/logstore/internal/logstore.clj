(ns balabit.logstore.internal.logstore
  "Internal functions, to be used by the balabit.logstore namespace"

  (:require [balabit.logstore.record :as lgs-record]))

(defmacro defrecflagq
  "Define a record flag query macro. Takes a name, and a flag to
  query, returns a macro that does just that."
  [flag]
  (let [name (symbol (str "logstore-record." flag "?"))
        keyflag (keyword flag)]
    `(defmacro ~name [& ~'record]
       `(lgs-record/flag-set? (or ~@~'record balabit.logstore/*logstore-record*) ~~keyflag))))

(defmacro make-record-flag-accessors
  [& flags]
  `(do ~@(map (fn [q] `(defrecflagq ~q)) flags)))
