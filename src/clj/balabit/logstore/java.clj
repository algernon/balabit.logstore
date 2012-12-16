(ns balabit.logstore.java
  "## The Java API"

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (java.nio ByteBuffer))
  (:use [balabit.logstore.sweet]
        [balabit.logstore.codec])
  (:gen-class :methods [#^{:static true} [fromFile [String] Object]
                        #^{:static true} [messages [Object] clojure.lang.LazySeq]
                        #^{:static true} [fromBuffer [java.nio.ByteBuffer] Object]]
              :name BalaBit.LogStore))

;; The Java API is very, very simple, it only consists of four
;; methods, that expose the parser to Java. Since all it does is
;; return a lazily build structure, that can already be accessed from
;; Java as-is, without much further help.
;;
;; One will, however, need to import `clojure.lang.LazySeq` and
;; `clojure.lang.Keyword` along with `BalaBit.LogStore` when dealing
;; with LogStore files from Java. The former two are needed to explore
;; the parsed data structure.
;;
;; As an example, this is how a simple LogStore displayer would look:
;;
;;     import BalaBit.LogStore;
;;     import clojure.lang.LazySeq;
;;     import clojure.lang.Keyword;
;;     import java.util.Map;
;;
;;     public class LGSCat {
;;       public static void main(String[] args) {
;;         Keyword k = Keyword.intern ("MESSAGE");
;;         Object lgs = BalaBit.LogStore.fromFile (args[0]);
;;         LazySeq s = BalaBit.LogStore.messages (lgs);
;;
;;         for (Object m : s) {
;;           Map msg = (Map) m;
;;
;;           System.out.print(msg.get[k]);
;;         }
;;       }
;;     }
;;
;; More examples can be found within the [source tree][src-java].
;;
;;  [src-java]: https://github.com/algernon/balabit.logstore/tree/master/src/java/
;;

(defn -fromFile
  "Java wrapper around balabit.logstore.sweet/from-file."
  [#^String fn]
  (from-file fn))

(defn -messages
  "Java wrapper around balabit.logstore.sweet/messages."
  [logstore]
  (messages logstore))

(defn -fromBuffer
  "Java wrapper around balabit.logstore.codec/decode-logstore."
  [#^ByteBuffer buff]
  (decode-logstore buff))

