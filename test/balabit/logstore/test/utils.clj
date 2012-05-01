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

(defn is-exception?
  "Check whether the exception `e` is of the `expected` class, or if
  it has a cause, whether the clause is of the `expected` class."
  [e expected]

  (if (.getCause e)
    (= (class (.getCause e)) expected)
    (= (class e) expected)))
