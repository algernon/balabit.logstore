(ns balabit.logstore.cli.search-predicates
  "## Search predicates for the CLI"

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:import (org.joda.time DateTime))
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

  (fn [msg] (some #{tag} (-> msg :TAGS))))

(defn severity
  "Match only those messages that have the given severity, or -
  optionally - those that bear a given relation with the specified
  severity, where relation is a function taking two arguments."

  ([sev] (severity = sev))
  ([cmp sev] (fn [msg]
               (let [sev-map (zipmap (reverse severity-map) (range))]
                 (when (cmp (get sev-map (-> msg :SEVERITY))
                            (get sev-map sev))
                   true)))))

(defn facility
  "Match only those messages that were sent using the specified
  facility."

  [fac] (fn [msg] (when (= fac (-> msg :FACILITY))
                    true)))

(defn ===
  "Matches messages where the last argument can be found by following
  the path laid out by the rest."

  [& args] (fn [msg]
             (when (= (get-in msg (butlast args)) (last args))
               true)))

(defn re-match
  "Matches messages where the a specified (or the message itself, if
  none) part matches the given regular expression"

  ([field pattern] (let [p (if (= (type pattern) java.util.regex.Pattern)
                             pattern
                             (re-pattern pattern))]
                     (fn [msg] (re-find p (field msg)))))

  ([pattern] (re-match :MESSAGE pattern)))

;; While re-match is quite clear, it is also fairly long. Since we
;; can't have =~ due to ~ being a special form of unquote, use ?= as
;; an alias for re-match.
(def ?= re-match)

;; ### Date-related predicates

(defn date
  "Matches messages where a specified field (or :TIMESTAMP) has a
  given relationship (:before, :on or :after) with the specified
  date (which must be a string)."

  ([rel field date-spec]
     (let [p-date (DateTime/parse date-spec)]
       (fn [msg]
         (when (= (.compareTo (field msg) p-date) (rel {:before -1, :on 0, :after 1}))
           true))))

  ([rel date-spec] (date rel :TIMESTAMP date-spec)))

(defn before
  "Matches messages that have a timestamp prior to the given date."

  [& args]

  (apply date :before args))

(defn on
  "Matches messages that have the exact timestamp that was given."

  [& args]

  (apply date :on args))

(defn after
  "Matches messages that have a timestamp past the given date."

  [& args]

  (apply date :after args))
