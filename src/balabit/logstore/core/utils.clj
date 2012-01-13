(ns balabit.logstore.core.utils
  "Miscellaneous utility functions."

  (:import (java.io InputStream)
           (java.nio ByteBuffer)))

(defn bb-read-bytes
  "Read a given amount of bytes from a ByteBuffer, and return them
as a byte array."
  [handle length]
  (let [buffer (make-array (. Byte TYPE) length)]
    (.get handle buffer 0 length)
    buffer))

(defn bb-read-block
  "Read a length-prefixed block from a ByteBuffer, and return the
result as a byte array."
  [handle]

  (let [length (.getInt handle)]
    (bb-read-bytes handle length)))

(defn bb-buffer-stream
  "Returns an InputStream for a ByteBuffer, such as returned by mmap."
  [#^ByteBuffer buf]
  (proxy [InputStream] []
    (available [] (.remaining buf))
    (read
      ([] (if (.hasRemaining buf) (.get buf) -1))
      ([dst offset len] (let [actlen (min (.remaining buf) len)]
                          (.get buf dst offset actlen)
                          (if (< actlen 1) -1 actlen))))))
