(ns balabit.logstore.utils
  "Miscellaneous utility functions.")

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
