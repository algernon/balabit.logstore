(ns balabit.logstore.cli.search-predicates
  "## Search predicates for the CLI"
  
  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:use [balabit.logstore.utils]))

;; All of the functions here are syntatic sugar, to be used with the
;; search command of the [CLI][cli]. Each function returns another
;; function, suitable for using as a filter predicate.
;;
;; [cli]: #balabit.logstore.cli
;;
;; A few examples:
;;
;;     lgstool search log.store '(tag :s_tcp)'
;;     lgstool search log.store '(severity >= :notice)'
;;     lgstool search log.store '(facility :mail)'
;;     lgstool search log.store '(=== :a-field "a-value")'
;;     lgstool search log.store '(re-match #"[aA]*[zZ]")'

(defn tag
  "Match only those messages that have the given tag among their
  tags."

  [tag]

  (fn [msg] (some #{tag} (-> msg :meta :tags))))

(defn severity
  "Match only those messages that have the given severity, or -
  optionally - those that bear a given relation with the specified
  severity, where relation is a function taking two arguments."

  ([sev] (severity = sev))
  ([cmp sev] (fn [msg]
               (let [sev-map (zipmap (reverse severity-map) (range))]
                 (cmp (get sev-map (-> msg :meta :severity))
                      (get sev-map sev))))))

(defn facility
  "Match only those messages that were sent using the specified
  facility."

  [fac] (fn [msg] (= fac (-> msg :meta :facility))))

(defn ===
  "Matches messages where the last argument can be found by following
  the path laid out by the rest."

  [& args] (fn [msg] (= (get-in msg (butlast args)) (last args))))

(defn re-match
  "Matches messages where the message parts matches the given regular
  expression"

  [pattern] (let [p (if (= (type pattern) java.util.regex.Pattern)
                      pattern
                      (re-pattern pattern))]
              (fn [msg] (re-find p (:MESSAGE msg)))))
