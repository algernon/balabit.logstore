(ns balabit.logstore.java
  "## The Java API"

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (java.nio ByteBuffer)
           (java.util Map)
           (BalaBit.LogStore LGSFormatException)
           (BalaBit.LogStore LGSChecksumException))
  (:use [balabit.logstore.sweet]
        [balabit.logstore.codec]
        [slingshot.slingshot :only [try+]]))

;; The Java API is very, very simple: it exposes only two classes:
;; `LogStoreMap` and `LogStore`, each of which have only a couple of
;; methods, that allow one to parse and explore a LogStore file.
;;
;; As an example, this is how a simple LogStore displayer would look:
;;
;;     import BalaBit.LogStore;
;;     import BalaBit.LogStoreMap;
;;
;;     public class LGSCat {
;;       public static void main(String[] args) {
;;         LogStore lgs = new BalaBit.LogStore (args[0]);
;;
;;         for (Object m : lgs.messages ()) {
;;           LogStoreMap msg = new LogStoreMap (m);
;;
;;           System.out.print(msg.get("MESSAGE"));
;;         }
;;       }
;;     }
;;
;; More examples can be found within the [source tree][src-java].
;;
;;  [src-java]: https://github.com/algernon/balabit.logstore/tree/master/src/java/
;;

;; ## LogStoreMap
;;
;; The LogStoreMap is a simple wrapper around a standard Clojure map,
;; which has one and one function only: retrieve a given key. Behind
;; the scenes, it will convert the string to a keyword, and look it up
;; in the original map.
;;
;; The usage is simple:
;;
;;     LogStoreMap m = new LogStoreMap (clojure_map);
;;
;; This, of course, means that one will have to be aware when to use
;; it, it is not automatic just yet.
;;
;; The class implements the `java.util.Map` interface, where keys are
;; always strings, and values can be either strings, or other maps.
;;
(gen-class :name BalaBit.LogStoreMap
           :methods [[get [String] Object]]
           :constructors {[Object] []}
           :implements [java.util.Map]
           :init init
           :state state
           :prefix lsm-)

(defn lsm-init
  "Constructor for a LogStoreMap object, takes a clojure map as input,
  and creates a wrapper around it."

  [o]

  [[Object] o])

(defn lsm-get
  "Get an entry from the LogStoreMap object, by converting the key to
  keyword first, and looking it up in the wrapped map."

  [#^BalaBit.LogStoreMap this o]

  (get (.state this) (keyword o)))

(defn lsm-entrySet
  "Return all the key-value entries within the LogStoreMap, with the
  keys converted to strings."

  [#^BalaBit.LogStoreMap this]

  (let [ks (keys (.state this))
        vs (vals (.state this))]
    (.entrySet #^Map (zipmap (map name ks) vs))))

(defn lsm-containsKey
  "Returns true when the key (converted to keyword first) is contained
  within the LogStoreMap."

  [#^BalaBit.LogStoreMap this key]

  (.containsKey #^Map (.state this) (keyword key)))

(defn lsm-containsValue
  "Returns true when the supplied value is contained within the LogStoreMap."

  [#^BalaBit.LogStoreMap this value]

  (.containsValue #^Map (.state this) value))

(defn lsm-equals
  "Returns true when the LogStoreMap is equal to the supplied object."

  [#^BalaBit.LogStoreMap this o]

  (.equals #^Map (.state this) o))

(defn lsm-hashCode
  "Returns the hashCode of the embedded map."

  [#^BalaBit.LogStoreMap this]

  (.hashCode #^Map (.state this)))

(defn lsm-isEmpty
  "Returns true if the LogStoreMap is empty."

  [#^BalaBit.LogStoreMap this]

  (.isEmpty #^Map (.state this)))

(defn lsm-keySet
  "Returns a set with the keys from the LogStoreMap, with all keys
  converted to strings first."

  [#^BalaBit.LogStoreMap this]

  (set (map name (keys (.state this)))))

(defn lsm-size
  "Returns the size of the internal map."

  [#^BalaBit.LogStoreMap this]

  (.size #^Map (.state this)))

(defn lsm-values
  "Returns the values within the LogStoreMap."

  [#^BalaBit.LogStoreMap this]

  (.values #^Map (.state this)))


;; ## LogStore
;;
;; The LogStore class exposes the parsing API itself: its constructor
;; takes either a filename, or a `ByteBuffer`, and will parse
;; either. Otherwise, it behaves the same way as `LogStoreMap` does.
;;
;;
(gen-class :name BalaBit.LogStore
           :prefix lgs-
           :init init
           :extends BalaBit.LogStoreMap
           :constructors {[String] [Object]
                          [java.nio.ByteBuffer] [Object]}
           :methods [[messages [] clojure.lang.LazySeq]])

(defn lgs-init
  "Create a LogStore object, by parsing either the file, or the supplied buffer."

  [o]

  (try+
   (if (= (type o) String)
     [[(from-file o)]]
     [[(decode-logstore o)]])

   (catch [:type :logstore/format-error] {:keys [message source assertion]}
     (throw (LGSFormatException. (str message "; source=" source
                                      "; assertion=" assertion))))


   (catch [:type :logstore/checksum-mismatch] {:keys [message source assertion
                                                      chunk-hmac expected-hmac]}
     (throw (LGSChecksumException. (str message "; source=" source
                                        "; assertion=" assertion
                                        "; chunk-hmac=" chunk-hmac
                                        "; expected-hmac=" expected-hmac))))))

(defn lgs-messages
  "Return all the messages present in the LogStore object."

  [#^BalaBit.LogStore this]

  (messages (.state this)))
