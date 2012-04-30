;;
;; Down below, we'll explore a few simple examples that shows how to
;; use the library from Clojure.
;;
;; All of the examples can be copy & pasted right into the REPL, or
;; can be imported and used that way. It is also possible to run the
;; various examples via lein:
;;
;; `lein run -m balabit.logstore.examples/lgstool-cat` **`filename`**
;;

(ns balabit.logstore.examples
  "## Examples"

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "GNU General Public License - v3"
              :url "http://www.gnu.org/licenses/gpl.txt"}}

  (:use [clojure.java.io :only [resource]])
  (:require [balabit.logstore :as logstore]))

;; # Meta-data printer
;; - - - - - - - - - -

(defn meta-data-printer
  "As an example, lets write a program that opens a LogStore file,
prints out some metadata, then loops over all the records, and
prints out some metadata about those, too!"
  ([] (meta-data-printer (resource "logstores/loggen.compressed.store")))
  ([filename]
     (logstore/with-file filename
       (println "Logstore meta-data")
       (println " - Magic  :" (-> (logstore/header) :magic))
       (println " - Crypto")
       (println "   + Hash :" (-> (logstore/header) :crypto :algo-hash))
       (println "   + Crypt:" (-> (logstore/header) :crypto :algo-crypt))
       (println " - Records:" (count (logstore/records)))
       (println)

       (loop [index 0]
         (when (< index (count (logstore/records)))
           (do
             (logstore/with-record index
               (println (str "Chunk #" (-> (logstore/record) :chunk-id)))
               (println " - Type :" (-> (logstore/record) :header :type))
               (println " - Flags:" (-> (logstore/record) :header :flags))
               (println " - Zip'd:" (logstore/compressed?))
               (println " - Msgs :"
                        (-> (logstore/record) :first-msgid) " - "
                        (-> (logstore/record) :last-msgid)))
             (recur (inc index))))))))

;; # Random message printer
;; - - - - - - - - - - - -

(defn random-message-printer
  "Lets write a function that opens a logstore, and prints a random
message from it. We do this by first selecting a random block, and
from that, a random message."
  ([] (random-message-printer (resource "logstores/loggen.compressed.store")))
  ([filename]
     (logstore/with-file filename
       (logstore/with-record (rand-int (-> (logstore/header) :last-block-id))
         (let [rand-idx (rand-int (- (-> (logstore/record) :last-msgid)
                                     (-> (logstore/record) :first-msgid)))]
           (println "Your random message is"
                    (+ rand-idx (-> (logstore/record) :first-msgid))
                    "from chunk"
                    (str "#" (-> (logstore/record) :chunk-id) ":"))
           (println (nth (-> (logstore/record) :messages) rand-idx)))))))

;; # Check timestamps
;; - - - - - - - - -

(defn check-timestamps
  "This function iterates over a logstore, and attempts to find all
the timestamps, and verifies that all chunks have a timestamp.

Verification is done in a very simple way: we count the number of
`:chunk` and `:timestamp` type records, and compare their number."
  ([] (check-timestamps (resource "logstores/timestamped.store")))
  ([filename]
     (logstore/with-file filename
       (let [is-type (fn [type what] (= type (:type what)))
             chunks (count (filter (partial is-type :chunk) (logstore/records)))
             timestamps (count (filter (partial is-type :timestamp) (logstore/records)))
             unstamped (- chunks timestamps)]
         (if (= 0 unstamped)
           (println "Congratulations, all your chunks are stamped!")
           (println "Oh boy, there are" unstamped "unstamped chunks!"))))))

;; # Print all messages from a logstore
;; - - - - - - - - - - - - - - - - - -

(defn lgstool-cat
  "This function will print all messages, and only the messages from
a LogStore.

This is done by looping over the record indexes, and printing the
messages if the record's a chunk."
  ([] (lgstool-cat (resource "logstores/short.compressed.store")))
  ([filename]
     (logstore/with-file filename
       (let [indexed-records (zipmap (range (count (logstore/records)))
                                     (logstore/records))
             chunk-records (keys (filter #(= :chunk (:type (val %)))
                                         indexed-records))
             print-msgs (fn [index]
                          (dorun (map println (:messages (logstore/nth-record index)))))]
         (dorun (map print-msgs chunk-records))))))

(defn -main
  "This function is mostly for convenience, so that one can call all
the examples within this namespace, even if one only has a JAR file."
  ([] nil)
  ([method] ((resolve (symbol "balabit.logstore.examples" method))))
  ([method param] ((resolve (symbol "balabit.logstore.examples" method)) param)))
