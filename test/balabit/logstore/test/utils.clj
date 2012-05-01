(ns balabit.logstore.test.utils
  (:use [slingshot.slingshot :only [try+]]))

(defmacro catch+
  "Wrap the `body` in a try+/catch (via slingshot), and catch
  exceptions limited to `catch-phrase`. The exception must have a
  *message* key, that will be returned by this function (or nil)."
  [catch-phrase & body]

  `(try+
    (do ~@body)
    (catch ~catch-phrase {:keys [~'message]}
      ~'message)))

(defmacro catchE
  "Wrap the `body` in a try/catch, and run the cought exception
  through is-exception?, checking for `catch-what`."
  [catch-what & body]

  `(try
     (do ~@body)
     (catch Exception ~'e
       (is-exception? ~'e ~catch-what))))

(defn is-exception?
  "Check whether the exception `e` is of the `expected` class, or if
  it has a cause, whether the clause is of the `expected` class.

  Returns the message of the exception."
  [e expected]

  (let [real-exception (or (.getCause e) e)]
    (when (= (class real-exception) expected)
      (.getMessage real-exception))))
