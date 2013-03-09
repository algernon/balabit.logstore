(ns balabit.logstore.cli
  "## Command-line interface"

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012-2013 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:require [balabit.logstore.sweet :as logstore])
  (:use [clojure.tools.cli :only [cli]]
        [clostache.parser]
        [clojure.pprint]
        [balabit.logstore.cli.search-predicates]
        [balabit.logstore.visualisation.gource]))

;; The command-line interface provides a few handy functions to
;; inspect LogStores with, without having to write Clojure code. They
;; both serve as an example, and also as very simple tools.
;;
;; All of the examples should be run as:
;;
;;     lein lgstool command <filename> [args]
;;

(defn print-message
  "Print a message, either as-is, or using a {{mustache}} template, or
  with pretty-printing enabled."

  [template message]

  (case template
    nil (prn message)
    :pretty (pprint message)
    (println (render template message))))

(defn- parse-cli-args
  "Parse command-line arguments for a given command. Takes a command
  name, the argument vector to parse, and an argument-spec, and either
  prints the help (and exists), or returns a vector of parsed
  parameters, the file name passed in, and any other optional
  arguments remaining."

  [cmd args & arg-spec]

  (let [[params [fn & opt-args] banner] (apply (partial cli args)
                                               (remove nil? (cons ["-h" "--help" "Show help"
                                                                   :default false :flag true] arg-spec)))]

    (when (:help params)
      (println "lgstool" cmd "\n")
      (println banner)
      (System/exit 0))

    [params fn opt-args]))

(defn lgstool-cat
  "Display all messages from a LogStore file, optionally with a
  template."

  [printer & args]

  (let [[params fn _] (parse-cli-args "cat" args
                                      ["-t" "--template" "Use a {{mustache}} template for output"])]
    (map (partial printer (:template params))
         (logstore/messages (logstore/from-file fn)))))

(defn lgstool-random
  "Display a random message from a LogStore file, optionally with a
  template."

  [printer & args]

  (let [[params fn _] (parse-cli-args "random" args
                                    ["-t" "--template" "Use a {{mustacher}} template for output"])]

    (printer (:template params) (rand-nth (logstore/messages (logstore/from-file fn))))))

(defn- with-all-predicates
  "Returns a function that takes one argument, and runs all predicates
  specified to this function, until it finds one that is false, or
  reaches the end of the list.

  Returns trueish when all predicates matched, falsy otherwise."

  [preds]

  (fn [msg]
    (not-any? nil? (map #((eval (read-string %)) msg) preds))))

(defn lgstool-search
  "Display messages matching a predicate. The predicate can be any
  clojure code that is valid as a filter predicate."

  [printer & args]

  (let [[params fn search-preds] (parse-cli-args "search" args
                                                 ["-t" "--template" "Use a {{mustache}} template for output"])]

    (in-ns 'balabit.logstore.cli)

    (map (partial printer (:template params))
         (filter (with-all-predicates search-preds)
                 (logstore/messages (logstore/from-file fn))))))

(defn lgstool-tail
  "Display the last N messages of a LogStore, optionally with a
  template. N is handled the same way as tail(1) handles it."

  [printer & args]

  (let [[params fn _] (parse-cli-args "tail" args
                                      ["-t" "--template" "Use a {{mustache}} template for output"]
                                      ["-n" "--lines" "Output only the last K lines"
                                       :default "10"])]

    (if (.startsWith (:lines params) "+")
      (map (partial printer (:template params))
           (drop (read-string (:lines params)) (logstore/messages (logstore/from-file fn))))
      (map (partial printer (:template params))
           (take-last (Integer. (:lines params)) (logstore/messages (logstore/from-file fn)))))))

(defn lgstool-head
  "Display the first N messages of a LogStore, optionally with a
  template. N is handled the same way as head(1) handles it."

  [printer & args]

  (let [[params fn _] (parse-cli-args "head" args
                                      ["-t" "--template" "Use a {{mustache}} template for output"]
                                      ["-n" "--lines" "Output only the first K lines"
                                       :default "10"])]

    (if (.startsWith (:lines params) "-")
      (map (partial printer (:template params))
           (drop-last (- (read-string (:lines params))) (logstore/messages (logstore/from-file fn))))
      (map (partial printer (:template params))
           (take (Integer. (:lines params)) (logstore/messages (logstore/from-file fn)))))))

(defn lgstool-inspect
  "Inspect a LogStore file, dumping its decoded contents back out as-is."

  [printer & args]

  (let [[params fn _] (parse-cli-args "inspect" args)]

    (printer :pretty (logstore/from-file fn))))

(defn lgstool-gource
  "Display a Gource-based visualisation of the LogStore parsing process."

  [_ & args]

  (let [[params fn [out-file _]] (parse-cli-args "gource" args)]

    (if out-file
      (spit out-file (logstore->gource fn))
      (with-gource fn))))

(defn lgstool-help
  "Display a help overview."

  [& _]

  (println "Usage: lgstool <command> [options] <filename>\n")
  (println "Available commands:")
  (println " cat     [-t|--template=TEMPLATE]")
  (println " tail    [-t|--template=TEMPLATE] [-n|--lines=N]")
  (println " head    [-t|--template=TEMPLATE] [-n|--lines=N]")
  (println " search  [-t|--template=TEMPLATE] <search-term>")
  (println " random  [-t|--template=TEMPLATE]")
  (println " gource")
  (println " inspect"))

(defn -main
  "Main entry point when running with leiningen, dispatches to any of
  the above functions."

  ([cmd & args]

     (if-let [cmd (ns-resolve 'balabit.logstore.cli (symbol (str "lgstool-" cmd)))]
       (dorun (apply (partial cmd print-message) args))
       (do
         (println "Unknown command:" cmd)
         (lgstool-help))))

  ([] (-main "help")))
