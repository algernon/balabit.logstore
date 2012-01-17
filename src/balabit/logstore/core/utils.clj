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

(defn- bitmap-find
  "Find out whether a bit is set in a Number, if so, add the same
  index entry from a bitmap to the accumulator, otherwise don't touch
  it. Returns the accumulator."
  [acc bitmap x n]
  (if (bit-test x n)
    (conj acc (nth bitmap n))
    acc))

(defn resolve-flags
  "Expand flags bitwise-OR'd together into symbolic names, using a
  bitmap table."
  [int-flags bitmap]

  (loop [index 0
         acc []]
    (if (< index (count bitmap))
      (recur (inc index) (bitmap-find acc bitmap int-flags index))
      acc)))

(defn flag-set?
  "Determines whether a given flag is set on a LogStore record header"
  [this flag]
  (or (some #(= flag %) (:flags this)) false))
