(ns balabit.logstore.core.utils
  "Miscellaneous utility functions."

  (:import (java.io InputStream)
           (java.nio ByteBuffer)))

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

(defn slice-n-dice
  "Cut a limited amount out from a ByteBuffer, optionally starting
from a specified offset."
  ([handle limit] (-> handle .slice (.limit limit)))
  ([handle offset limit] (-> handle (.position offset) .slice (.limit limit))))
