(ns balabit.logstore.core.utils
  "Miscellaneous utility functions."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "GNU General Public License - v3"
              :url "http://www.gnu.org/licenses/gpl.txt"}}

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
  from a specified offset. Returns a limited slice of the ByteBuffer."
  ([handle limit] (-> handle .slice (.limit limit)))
  ([handle offset limit] (-> handle (.position offset) .slice (.limit limit))))

(defn- bitmap-find
  "Find out whether a bit is set in a Number, if so, add the same
  index entry from a bitmap to the accumulator, otherwise don't touch
  it. Returns the accumulator.

  This can be used to turn a *flag-byte* into a list of flags: when
  each bit signals a different flag, this function can determine
  whether one bit is set, and which flag it corresponds to, using a
  bit-map."
  [acc bitmap x n]
  (if (bit-test x n)
    (conj acc (nth bitmap n))
    acc))

(defn resolve-flags
  "Expand flags bitwise-OR'd together into symbolic names, using a
  bit-map vector. The bitmap vector should be a vector, with one entry
  for each bit set.

  Thus, if we have a *flag* of **3** (the first two bits set), and a
  bit-map of `[:first-bit, :second-bit, :third-bit]`, then
  `(resolve-flags flag bit-map)` will yield
  `[:first-bit, :second-bit]`."
  [int-flags bitmap]

  (loop [index 0
         acc []]
    (if (< index (count bitmap))
      (recur (inc index) (bitmap-find acc bitmap int-flags index))
      acc)))

(defn flag-set?
  "Determines whether a given flag is set on a LogStore record
  header. Returns either true or false."
  [this flag]
  (or (some #(= flag %) (:flags this)) false))
