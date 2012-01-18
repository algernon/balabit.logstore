(ns balabit.logstore.core.errors
  "Exceptions and other error messages thrown by the library."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license "All rights reserved"})

(defmacro invalid-file [context, message]
  `{:type ::invalid-file
    :context ~context
    :message ~message})
