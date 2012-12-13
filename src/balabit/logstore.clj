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
;; But the general idea is to use
;; `(:require [balabit.logstore.sweet :as logstore])` or something
;; similar, and use the convenience functions provided therein.
;;
;; [4]: #balabit.logstore.examples
;;
;; # Limitations
;;
;; * This is a simple reader. It does not, and will not support
;;   writing LogStore files.
;; * It does not support reading from open LogStores. It's not guarded
;;   against, but the library assumes that the LogStore file is
;;   closed.
;; * The library is not thread-safe.
;;
;; # Unimplemented features
;;
;; Unlike limitations, that are unlikely to ever appear in the
;; library, there is a small set of unimplemented features, that
;; sooner or later, will find its sway into the library.
;;
;; * Encrypted logstores are not supported at all yet: the library
;;   will probably barf and throw exceptions when encountering one.
;; * Timestamps are extracted only as a binary blob, the timestamp
;;   part itself is not parsed yet.
;; * File and chunk hashes are extracted as binary data only, they are
;;   never checked, nor properly parsed.
;; * There is no Java API yet. In the future, we want to make the
;;   library easily accessible from Java aswell.
;;

(ns balabit.logstore)
