(ns balabit.logstore.codec.verify
  "## Verifying extracted data

   Verifying is done on an as-needed basis, the library does not
   verify everything, only a few selected key parts of the process. It
   does so by using the `verify-frame` multi-method below, which can
   dispatch to various verifiers, implemented right next to the
   decoders."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (use [clojure.core]))

(defmulti verify-frame
  "Verify a single frame. Dispatches on the type, raises an exception
  if verification fails, returns `data` otherwise."

  {:arglists '([data type & options])}

  (fn [_ type & _] type))

;; By default, we do not verification, and just return the data as-is.
(defmethod verify-frame :default
  [d & _]

  d)
