;; ## syslog-ng PE LogStore reader
;;
;; The balabit.logstore project is a library written in [Clojure][1],
;; that tries to provide a convenient API to read [syslog-ng PE][2]
;; [LogStore][3] files.
;;
;; [1]: http://clojure.org/
;; [2]: http://www.balabit.com/network-security/syslog-ng/central-syslog-server/overview
;; [3]: http://www.balabit.com/TBD
;; 
;; # Why?
;;
;; The reason behind the implementation is to have an independent,
;; open source reader for the [LogStore][3] file format, so that one
;; is not tied to [syslog-ng][2] to read one's logs stored in this
;; format. An open implementation makes it possible to read these logs
;; on systems where [syslog-ng][2] is not installed, or where the
;; `lgstool` program is not available.
;;
;; We chose [Clojure][1] as the implementation language for - among
;; others - the following reasons:
;;
;;   * It is a convenient language, with great Java interoperability,
;;     which we wanted to use (for example, to decompress data
;;     easily).
;;   * Clojure has a very good REPL, which allows us, and the
;;     potential users of this library, to quickly inspect certain
;;     aspects of a LogStore file, right from the REPL.
;;   * Being a JVM language, it's reasonably easy to provide a Java
;;     API aswell, on top of the Clojure library, making it readily
;;     usable for Clojure and Java developers alike.
;;
;; # How?
;;
;; For examples, see the [example][4] section of the documentation, or
;; the test suite in the source tree.
;;
;; [4]: #balabit.logstore.examples
;;
;; # Limitations
;;
;; * This is a simple reader. It does not, and will not support writing LogStore files.
;; * It does not support reading from open LogStores. It's not guarded
;;   against, but the library assumes that the LogStore file is
;;   closed.
;; * The library is not thread-safe. The LogStore file object does
;;   store state, using the same one from different threads will
;;   produce unpredictable results.
;;
;; # Known Bugs
;;
;; * Decompression is faulty: if there's more data to be read than
;;   what fits in the default buffer (512 bytes), decompression will
;;   go astray.

(ns balabit.logstore
  "Public syslog-ng LogStore reader API"
  (:require [balabit.logstore.core.file :as lgs]
            [balabit.logstore.core.record :as lgs-record])
  (:use balabit.logstore.impl.logstore)
  (:import balabit.logstore.core.file.LSTFile)
  (:import balabit.logstore.core.record.LSTRecord))

(declare ^:dynamic ^LSTFile *logstore*)
(declare ^:dynamic ^LSTRecord *logstore-record*)

;;
;; # LogStore file convenience macros
;;
;; On the highest level, we deal with LogStore files: we want to keep
;; one open, work with its file headers, and the records contained
;; therein.
;;
;; All of these - except for itself - must be called from within the
;; body of a `(with-logstore)`.
;;
;; - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
;;

(defmacro with-logstore
  "Evaluates body, with the specified LogStore file already opened,
and bound to `*logstore*`."
  [filename & body]
  `(binding [*logstore* (lgs/open ~filename)]
     (do ~@body)))

(defmacro logstore-header
  "Returns the full header of the already opened LogStore file, or, if
`chain` is specified, then retrieves each key in the chain, using the
result of the previous iteration as input."
  [& chain]
  `(reduce get (:header *logstore*) [~@chain]))

(defmacro logstore-records
  "Returns the record headers in an opened LogStore file."
  []
  `(:record-map *logstore*))

(defmacro logstore-nth
  "Returns the Nth record from an opened LogStore file."
  [index]
  `(lgs/nth *logstore* ~index))

;;
;; # LogStore record convenience macros
;;
;; LogStore files are built up from records, where each record has a
;; common set of header fields, and - depending on type -
;; type-specific headers and data.
;;
;; The macros below make it easier to work with these records, and
;; similar to the LogStore file macros, these must also be called from
;; within the body of a `(with-logstore-record)`, except for itself,
;; of course.
;;
;; - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
;;

(defmacro with-logstore-record
  "Evaluates body, with the specified record retrieved and bound to
`*logstore-record*`."
  [index & body]
  `(binding [*logstore-record* (logstore-nth ~index)]
     (do ~@body)))

(defmacro logstore-record
  "Returns the full header of the current LogStore record, or, if
`chain` is specified, then retrieves each key in the chain, using the
result of the previous iteration as input."
  [& chain]
  `(reduce get *logstore-record* [~@chain]))

;; Create a set of flag accessors, that return true or false,
;; depending on whether a given flag was set on a LogStore record, or
;; not.
;;
;; The accessors are named after the flag they check, and are prefixed
;; with `logstore-record.`, ending up with such names as
;; `logstore-record.compressed?`.
;;
;; These, unlike the other macros, do not need to be called from
;; within a `(with-logstore-record)`, but can take an optional record
;; parameter, and work with that instead of the default.
(make-record-flag-accessors compressed encrypted broken serialized)
