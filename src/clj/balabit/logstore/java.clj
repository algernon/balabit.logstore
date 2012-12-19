(ns balabit.logstore.java
  "## The Java API"

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (java.nio ByteBuffer)
           (java.util Map))
  (:use [balabit.logstore.sweet]
        [balabit.logstore.codec]))

;; The Java API is very, very simple: it exposes only two classes:
;; `LogStoreMap` and `LogStore`, each of which have only a couple of
;; methods, that allow one to parse and explore a LogStore file.
;;
;; As an example, this is how a simple LogStore displayer would look:
;;
;;     import BalaBit.LogStore;
;;     import BalaBit.LogStoreMap;
;;     import clojure.lang.LazySeq;
;;
;;     public class LGSCat {
;;       public static void main(String[] args) {
;;         LogStore lgs = new BalaBit.LogStore (args[0]);
;;         LazySeq s = lgs.messages ();
;;
;;         for (Object m : s) {
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

  [this, o]

  (get (.state this) (keyword o)))

(defn lsm-entrySet
  "Return all the key-value entries within the LogStoreMap, with the
  keys converted to strings."

  [this]

  (let [ks (keys (.state this))
        vs (vals (.state this))]
    (.entrySet (zipmap (map name ks) vs))))

(defn lsm-containsKey
  "Returns true when the key (converted to keyword first) is contained
  within the LogStoreMap."

  [this key]

  (.containsKey (.state this) (keyword key)))

(defn lsm-containsValue
  "Returns true when the supplied value is contained within the LogStoreMap."
  
  [this value]

  (.containsValue (.state this) value))

(defn lsm-equals
  "Returns true when the LogStoreMap is equal to the supplied object."
  
  [this o]

  (.equals (.state this) o))

(defn lsm-hashCode
  "Returns the hashCode of the embedded map."
  
  [this]

  (.hashCode (.state this)))

(defn lsm-isEmpty
  "Returns true if the LogStoreMap is empty."
  
  [this]

  (.isEmpty (.state this)))

(defn lsm-keySet
  "Returns a set with the keys from the LogStoreMap, with all keys
  converted to strings first."

  [this]

  (set (map name (keys (.state this)))))

(defn lsm-size
  "Returns the size of the internal map."
  
  [this]

  (.size (.state this)))

(defn lsm-values
  "Returns the values within the LogStoreMap."

  [this]

  (.values (.state this)))


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

  (if (= (type o) String)
    [[(from-file o)]]
    [[(decode-logstore o)]]))

(defn lgs-messages
  "Return all the messages present in the LogStore object."

  [this]

  (messages (.state this)))
