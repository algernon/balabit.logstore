(ns balabit.logstore.core.errors
  "Exceptions and other error messages thrown by the library.")

(defmacro invalid-file
  "Invalid file exception, thrown when a LogStore file is found
  invalid, for one reason or the other. The `context` parameter should
  be a keyword, that signals which part of the file was found invalid,
  the `message` is a human-readable explanation of it."

  [context message]
  `{:type :invalid-file
    :context ~context
    :message ~message})
