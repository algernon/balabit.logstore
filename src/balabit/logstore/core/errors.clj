(ns balabit.logstore.core.errors
  "Exceptions and other error messages thrown by the library."

    ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "GNU General Public License - v3"
              :url "http://www.gnu.org/licenses/gpl.txt"}})

(defmacro invalid-file
  "Invalid file exception, thrown when a LogStore file is found
  invalid, for one reason or the other. The `context` parameter should
  be a keyword, that signals which part of the file was found invalid,
  the `message` is a human-readable explanation of it."

  [context message]
  `{:type :invalid-file
    :context ~context
    :message ~message})
