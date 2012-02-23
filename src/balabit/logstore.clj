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
;; But the general idea is to use `(:require [balabit.logstore :as logstore])`
;; or something similar, and use the macros provided herein, instead
;; of `(:use balabit.logstore)`.
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
;; # Unimplemented features
;;
;; Unlike limitations, that are unlikely to ever appear in the
;; library, there is a small set of unimplemented features, that
;; sooner or later, will find its sway into the library.
;;
;; * Serialized messages are not completely deserialized yet, the
;;   name-value pairs are ignored right now.
;; * Encrypted logstores are not supported at all yet: the library
;;   will probably barf and throw exceptions when encountering one.
;; * Timestamps are extracted only as a binary blob, the timestamp
;;   part itself is not parsed yet.
;; * File and chunk hashes are extracted as binary data only, they are
;;   never checked, nor properly parsed.
;; * There is no Java API yet. In the future, we want to make the
;;   library easily accessible from Java aswell.
;;

(ns balabit.logstore
  "Public syslog-ng LogStore reader API"

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license "All rights reserved"}
  
  (:require [balabit.logstore.core.file :as lgs]
            [balabit.logstore.core.record :as lgs-record])
  (:use balabit.logstore.core.utils)
  (:import balabit.logstore.core.file.LSTFile))

(declare ^:dynamic ^LSTFile *logstore*)
(declare ^:dynamic *logstore-record*)

;;
;; # LogStore file convenience macros
;;
;; On the highest level, we deal with LogStore files: we want to keep
;; one open, work with its file headers, and the records contained
;; therein.
;;
;; All of these - except for itself - must be called from within the
;; body of a `(with-file)`.
;;
;; ### File meta-data
;;
;; Opening a LogStore makes the file-metadata available via
;; `(header)`, which returns a record that has the following fields:
;;
;; * `:magic`: The magic four bytes identifying what version this
;;   LogStore is.
;; * `:flags`: Currently unused, and should be zero.
;; * `:last-block-id`: The ID of the last block in the file.
;; * `:last-chunk-id`: The ID of the last chunk in the file.
;; * `:last-block-end-offset`: The offset to the end of the last block
;;   in the file.
;; * `:crypto`: Crypto header, describing what hashing and encryption
;;   methods are used within the file.
;;
;; The crypto header contains the following fields:
;;
;; * `:algo-hash`: Hashing method, as a string.
;; * `:algo-crypt`: Encryption method, as a string.
;; * `:file-mac`: The MAC of the full file.
;; * `:der`: Currently unused and unsupported, but used for
;;   encryption.
;;
;; - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
;;

(defmacro with-file
  "Evaluates body, with the specified LogStore file already opened,
  and bound to `*logstore*`."
  [filename & body]
  `(binding [*logstore* (lgs/open ~filename)]
     (do ~@body)))

(defmacro header
  "Returns the full header of the already opened LogStore file, be
  that a specified logstore file, or if no arguments are given,
  `*logstore*`."
  ([] `(header *logstore*))
  ([#^LSTFile logstore] `(:header ~logstore)))

(defmacro records
  "Returns the record headers in an opened LogStore file, be that a
  specified logstore file, or if no arguments are given, `*logstore*`."
  ([] `(records *logstore*))
  ([#^LSTFile logstore] `(:record-map ~logstore)))

(defmacro nth-record
  "Returns the Nth record from an opened LogStore file, be that a
  specified logstore file, or if no arguments are given, `*logstore*`"
  ([index] `(nth-record *logstore* ~index))
  ([#^LSTFile logstore index] `(lgs/nth ~logstore ~index)))

;;
;; # LogStore record convenience macros
;;
;; LogStore files are built up from records, where each record has a
;; common set of header fields, and - depending on type -
;; type-specific headers and data.
;;
;; The macros below make it easier to work with these records, and
;; similar to the LogStore file macros, these must also be called from
;; within the body of a `(with-record)`, except for itself, of course.
;;
;; ### Record Formats
;;
;; A LogStore can store multiple types of records, each of which have
;; a different purpose, and as such, different fields and
;; properties. The record types recognised by this library are
;; explained below.
;;
;; All records have a common header, that contains the `:size`,
;; `:type` and `:flags` properties. The first one is the full size of
;; the block, with all headers included, the second is the type of the
;; chunk, and the third is a vector of flag symbols.
;;
;; Available flags are: `:compressed`, `:serialized`, `:encrypted` and
;; `:broken`. They signal that a block is compressed, serialized,
;; encrypted or broken, respectively.
;;
;; #### Chunk
;;
;; A chunk is a collection of messages, with a little meta-data. A
;; chunk object contains the following fields:
;;
;; * `:header`: The original record header, with fields explained above.
;; * `:start-time` and `:end-time`: The timestamp of the first and of
;;   the last message within the chunk, up to millisecond precision.
;; * `:first-msgid` and `:last-msgid`: The ID of the first and of the
;;   last message in the chunk.
;; * `:chunk-id`: The ID of this chunk.
;; * `:xfrm-offset`: Currently extracted, but unused value, will
;;   disappear in the future.
;; * `:messages`: A vector of decrypted, decompressed, string messages.
;; * `:flags`: A vector of flag symbols, specific to this chunk. Known
;;   flags are `:hmac` and `:hash`.
;; * `:file-mac`: Is the cryptographic hash of all the chunks
;;   contained in the file so far. The exact format of it depends on
;;   what was specified in the file headers.
;; * `:chunk-mac`: The cryptograhic hash of the current chunk. Same
;;   thing applies to it as for `:file-mac`.
;;
;; #### Timestamp
;;
;; A timestamp is a cryptographically secure timestamp, that can be
;; used to validate that a given chunk it refers to was made at a
;; given time.
;;
;; The object contains the following fields:
;;
;; * `:header`: The original record header, with fields explained above.
;; * `:chunk-id`: The ID of the chunk the timestamp refers to.
;; * `:timestamp`: The timestamp itself, as a ByteBuffer.
;;
;; - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
;;

(defmacro with-file-record
  "Evaluates `body`, with the specified record retrieved and bound to
  `*logstore-record*`. Requires an `LTSFile` as the first parameter,
  the record will be looked up from there."
  [#^LSTFile logstore index & body]
  `(binding [*logstore-record* (nth-record ~logstore ~index)]
     (do ~@body)))

(defmacro with-record
  "Evaluates `body`, with the specified record retrieved and bound to
  `*logstore-record*`. This works on the LogStore bound to
  `*logstore*`."
  [index & body]
  `(with-file-record *logstore* ~index ~@body))

(defmacro record
  "Returns the full header of the current LogStore record, unless
  another record is specified."
  ([] `(record *logstore-record*))
  ([rec] `~rec))

(defmacro def-record-flag-accessor
  "Define a record flag query macro. Takes a name, and a flag to
  query, returns a macro that does just that."
  [flag]
  (let [name (symbol (str flag "?"))
        keyflag (keyword flag)]
    `(defmacro ~name
       "Check whether the current record (if not arguments are given)
  or the specified one has the flag suggested by the name of the
  macro, set. Returns true if so, false otherwise."
       ([] `(flag-set? (:header balabit.logstore/*logstore-record*) ~~keyflag))
       ([~'record] `(flag-set? (:header ~~'record) ~~keyflag)))))

(defmacro make-record-flag-accessors
  "Make a set of flag accessors."
  [& flags]
  `(do ~@(map (fn [q] `(def-record-flag-accessor ~q)) flags)))

;; Create a set of flag accessors, that return true or false,
;; depending on whether a given flag was set on a LogStore record, or
;; not.
;;
;; The accessors are named after the flag they check, suffixed with a
;; question mark, ending up with such names as `compressed?`.
;;
;; These, unlike the other macros, do not need to be called from
;; within a `(with-record)`, but can take an optional record
;; parameter, and work with that instead of the default.
(make-record-flag-accessors compressed encrypted broken serialized)
