(ns balabit.logstore.utils
  "Miscellaneous utility functions.")

(defn bb-read-bytes
  "Read a given amount of bytes from a ByteBuffer, and return them
as a byte array."
  [handle length]
  (let [buffer (make-array (. Byte TYPE) length)]
    (.get handle buffer 0 length)
    buffer))
