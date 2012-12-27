(ns balabit.logstore.utils
  "## Utility functions"

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (org.joda.time DateTime DateTimeZone)
           (java.nio ByteBuffer)
           (java.net InetAddress)
           (java.io InputStream OutputStream)))

(defn- bitmap-find
  "Takes an accumulator, a map, an integer and an index. Checks
  whether the `index` bit of the integer is set, and if so, looks up the
  same index from the map, and adds it to the accumulator.

  Returns either the accumulator (either extended or unmodified)."

  [acc bitmap x n]

  (if (bit-test x n)
    (conj acc (nth bitmap n))
    acc))

(defn resolve-flags
  "Given a map, and an integer flagset, find all elements of the map
  which are set in the flagset."

  [bitmap int-flags]

  (loop [index 0
         acc []]
    (if (< index (count bitmap))
      (recur (inc index) (bitmap-find acc bitmap int-flags index))
      acc)))

(defn flag-set?
  "Checks whether the given flag is set in the flagset."

  [this flag]
  (or (some #(= flag %) (:flags this)) false))


(defn resolve-timestamp
  "Resolve a timestamp into DateTime objects. The `timespec` is a map
  that has at least `:sec` and `:usec` members, and can optionally
  have a `:zone-offset` (in seconds) too."

  [timespec]

  (if (:zone-offset timespec)
    (DateTime. (+ (* (long (:sec timespec)) 1000)
                  (quot (:usec timespec) 1000))
               (DateTimeZone/forOffsetMillis (* (:zone-offset timespec) 1000)))
    (DateTime. (+ (* (long (:sec timespec)) 1000)
                  (quot (:usec timespec) 1000)))))

(defn ->InputStream
  "Proxy a ByteBuffer into an InputStream, for functions that need the
  latter."

  [#^ByteBuffer buf]

  (proxy [InputStream] []
    (available [] (.remaining buf))
    (read
      ([] (if (.hasRemaining buf) (.get buf) -1))
      ([dst offset len] (let [actlen (min (.remaining buf) len)]
                          (.get buf dst offset actlen)
                          (if (< actlen 1) -1 actlen))))))


(defn stream-copy
  "Copy an InputStream to an OutputStream."

  [#^InputStream input #^OutputStream output]

  (let [buffer (make-array Byte/TYPE 1024)]
    (loop []
      (let [size (.read input buffer)]
        (when (pos? size)
          (do (.write output buffer 0 size)
              (recur)))))))

(defn resolve-address
  "Resolve the binary representation of an address into an InetAddress
  object."

  [#^ByteBuffer addr]

  (let [buffer (byte-array (.limit addr))]
    (.get addr buffer 0 (.limit addr))
    (InetAddress/getByAddress buffer)))

(defn array->hex
  "Convert a byte array to its hexadecimal representation, and return
  that as a string."

  [data-bytes]

  (apply str (map 
              #(.substring  (Integer/toString (+ (bit-and % 0xff) 0x100) 16) 1) 
              data-bytes)))

;; List of known syslog facilities.
(def facility-map
  [:kern
   :user
   :mail
   :daemon
   :auth
   :syslog
   :lpr
   :news
   :uucp
   :cron
   :authpriv
   :ftp
   :ntp
   :log-audit
   :log-alert
   :clock
   :local0
   :local1
   :local2
   :local3
   :local4
   :local5
   :local6
   :local7])

;; List of known syslog severities.
(def severity-map
  [:emergency
   :alert
   :critical
   :error
   :warning
   :notice
   :informational
   :debug])
