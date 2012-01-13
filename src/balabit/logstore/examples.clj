;;
;; Down below, we'll explore a few simple examples that shows how to
;; use the library from Clojure.
;;
;; All of the examples can be copy & pasted right into the REPL, or
;; the namespace can be used, and the functions used directly.
;;

(ns balabit.logstore.examples
  "## Examples"
  (:use balabit.logstore))

;; # Meta-data printer
;; - - - - - - - - - -

(defn meta-data-printer
  "As an example, lets write a program that opens a LogStore file,
prints out some metadata, then loops over all the records, and
prints out some metadata about those, too!"
  []
  (with-logstore "resources/loggen.store"
    (println "Logstore meta-data")
    (println " - Magic  :" (logstore-header :magic))
    (println " - Crypto")
    (println "   + Hash :" (logstore-header :crypto :algo_hash))
    (println "   + Crypt:" (logstore-header :crypto :algo_crypt))
    (println " - Records:" (count (logstore-records)))
    (println)

    (loop [index 0]
      (when (< index (count (logstore-records)))
        (do
          (with-logstore-record index
            (println (str "Chunk #" (logstore-record :chunk_id)))
            (println " - Type :" (logstore-record :header :type))
            (println " - Flags:" (logstore-record :header :flags))
            (println " - Zip'd:" (logstore-record.compressed?))
            (println " - Msgs :"
                     (logstore-record :first_msgid) " - "
                     (logstore-record :last_msgid)))
          (recur (inc index)))))))
