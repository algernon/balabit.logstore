(ns balabit.logstore.cli
  "## Command-line interface"
  
  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}
  
  (:require [balabit.logstore.sweet :as logstore])
  (:use [clojure.tools.cli :only [cli]]
        [clostache.parser]
        [clojure.pprint]))

;; The command-line interface provides a few handy functions to
;; inspect LogStores with, without having to write Clojure code. They
;; both serve as an example, and also as very simple tools.
;;
;; All of the examples should be run as:
;;
;;     lein run -m balabit.logstore.cli/function [args] <filename>
;;

(defn print-message
  "Print a message, either as-is, or using a {{mustache}} template."
  
  [template message]

  (if template
    (println (render template message))
    (prn message)))

(defn cat
  "Display all messages from a LogStore file, optionally with a
  template."

  [& args]

  (let [[params [fn _] banner] (cli args
                                    ["-t" "--template" "Use a {{mustache}} template for output"]
                                    ["-h" "--help" "Show help"
                                     :default false :flag true])]

    (when (:help params)
      (println banner)
      (System/exit 0))

    (dorun (map (partial print-message (:template params)) (logstore/messages (logstore/from-file fn))))))

(defn search
  [& args]

  (let [[params [fn search-pred _] banner]
        (cli args
             ["-t" "--template" "Use a {{mustache}} template for output"]
             ["-h" "--help" "Show help"
              :default false :flag true])]
    
    (when (:help params)
      (println banner)
      (System/exit 0))

    (dorun (map (partial print-message (:template params))
                (filter (eval (read-string search-pred))
                        (logstore/messages (logstore/from-file fn)))))))

(defn tail
  "Display the last N messages of a LogStore, optionally with a
  template. N is handled the same way as tail(1) handles it."

  [& args]

  (let [[params [fn _] banner] (cli args
                                    ["-t" "--template" "Use a {{mustache}} template for output"]
                                    ["-n" "--lines" "Output only the last K lines"
                                     :default "10"]
                                    ["-h" "--help" "Show help"
                                     :default false :flag true])]
    (when (:help params)
      (println banner)
      (System/exit 0))
    (if (.startsWith (:lines params) "+")
      (dorun (map (partial print-message (:template params))
                  (drop (read-string (:lines params)) (logstore/messages (logstore/from-file fn)))))
      (dorun (map (partial print-message (:template params))
                  (take-last (Integer. (:lines params)) (logstore/messages (logstore/from-file fn))))))))

(defn head
  "Display the first N messages of a LogStore, optionally with a
  template. N is handled the same way as head(1) handles it."

  [& args]

  (let [[params [fn _] banner] (cli args
                                    ["-t" "--template" "Use a {{mustache}} template for output"]
                                    ["-n" "--lines" "Output only the first K lines"
                                     :default "10"]
                                    ["-h" "--help" "Show help"
                                     :default false :flag true])]
    (when (:help params)
      (println banner)
      (System/exit 0))
    (if (.startsWith (:lines params) "-")
      (dorun (map (partial print-message (:template params))
                  (drop-last (- (read-string (:lines params))) (logstore/messages (logstore/from-file fn)))))
      (dorun (map (partial print-message (:template params))
                  (take (Integer. (:lines params)) (logstore/messages (logstore/from-file fn))))))))

(defn inspect
  "Inspect a LogStore file, dumping its decoded contents back out as-is."

  [& args]

  (let [[params [fn _] banner] (cli args
                                ["-h" "--help" "Show help"
                                 :default false :flag true])]

    (when (:help params)
      (println banner)
      (System/exit 0))

    (pprint (logstore/from-file fn))))

(defn help
  "Display a help overview."

  [& args]

  (println "Usage: lgstool <command> [options] <filename>\n")
  (println "Available commands:")
  (println " cat     [-t|--template=TEMPLATE]")
  (println " tail    [-t|--template=TEMPLATE] [-n|--lines=N]")
  (println " head    [-t|--template=TEMPLATE] [-n|--lines=N]")
  (println " search  [-t|--template=TEMPLATE] <search-term>")
  (println " inspect"))

(defn -main
  "Main entry point when running with leiningen, dispatches to any of
  the above functions."
  
  [cmd & args]

  (if-let [cmd (ns-resolve 'balabit.logstore.cli (symbol cmd))]
    (apply cmd args)
    (help)))
