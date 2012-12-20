(ns balabit.logstore.exceptions
  "## Custom exceptions"

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:use [slingshot.slingshot :only [throw+]]))

(defmacro assert-format
  "Evaluates an expression, and throws a slingshot exception with
  custom extra information if it does not evaluate to logical true. If
  it does, returns data."

  [data ex-info & body]

  `(if ~@body
     ~data
     (throw+ (merge {:type :logstore/format-error
                     :assertion '~@body}
                    ~ex-info))))

;; The LGSFormatException exception shall be thrown from Java, in case
;; of a parsing error.
(gen-class :name BalaBit.LogStore.LGSFormatException
           :extends java.lang.Exception)
