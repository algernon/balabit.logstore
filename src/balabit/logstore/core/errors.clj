(ns balabit.logstore.core.errors)

(defmacro invalid-file [context, message]
  `{:type ::invalid-file
    :context ~context
    :message ~message})
