(ns balabit.logstore.errors)

(defmacro invalid-file [context, message]
  `{:type ::invalid-file
    :context ~context
    :message ~message})
