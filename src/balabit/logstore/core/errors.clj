(ns balabit.logstore.core.errors
  "Exceptions and other error messages thrown by the library.")

(defmacro invalid-file [context, message]
  `{:type ::invalid-file
    :context ~context
    :message ~message})
