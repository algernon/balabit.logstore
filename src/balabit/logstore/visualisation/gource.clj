(ns balabit.logstore.visualisation.gource
  "## Visualizing the parsing process"
  
  (:require [clojure.string :as s]
            [conch.sh :refer [with-programs]])
  (:use [robert.hooke]
        [balabit.blobbity]
        [balabit.logstore.sweet]))

;; To visualise the parsing process, we'll be tracking three things
;; with an atom: the overall history, which is simply a list of
;; arrays, where each array contains the current *path*, the buffer,
;; the type `decode-frame` dispatched on, and an overall position
;; (more on that later).
;;
;; From these, we can draw a nice animation, if we imagine that as the
;; `decode-frame` calls call onto each other, we reach more and more
;; nodes of a directory tree: the dispatch types taken so far is the
;; path.
;;
;; The amount of bytes each `decode-frame` call processes will be used
;; as the time between this, and the next event.
;;
;; In the end, passing this to gource produces a fairly neat animation.
;;

;; The tracker atom, this is updated by the `track-frame` function.
(def tracker (atom {:history '()
                    :tree '()
                    :overall-position 0}))

(defn reset-tracker!
  "Reset the tracker atom to its initial, empty state."

  []

  (reset! tracker
          {:history '()
           :tree '()
           :overall-position 0}))

(defn- tree->path
  "From a tree, form a UNIX-like path, by joining it together."

  [tree]

  (s/join "/" (reverse (map #(apply str (rest (str %))) tree))))

(defn track-frame
  "Hook function that explores data before and after calling
  `decode-frame`, and updates the tracking atom with new information."

  [f buffer type & args]

  (swap! tracker assoc-in [:tree] (cons type (:tree @tracker)))
  (let [start-position (.position buffer)
        result (apply f buffer type args)
        pos-diff (- (.position buffer) start-position)]
    (swap! tracker update-in [:overall-position] + pos-diff)
    (swap! tracker update-in [:history]
           conj [(tree->path (:tree @tracker))
                 buffer type
                 (+ (:overall-position @tracker) pos-diff)])
    (swap! tracker update-in [:tree] rest)
    result))

(defn- history-line->gource-line
  "From an entry in the trackers history, produce a formatted line
  that Gource understands."
  
  [[path buffer type pos color]]
  
  (str (int (/ pos 10)) "|" type "|" "M" "|" path "\n"))

(defn history->gource
  "Convert an entire history to a format understood by Gource."
  [history]

  (apply str (map history-line->gource-line history)))

(defn logstore->gource
  "Analyse a LogStore file, and create the source of a Gource
  animation based on it."

  [lgs]

  (reset-tracker!)
  (remove-hook #'decode-frame #'track-frame)
  (add-hook #'decode-frame #'track-frame)
  (dorun (messages (from-file lgs)))
  (remove-hook #'decode-frame #'track-frame)
  (history->gource (reverse (:history @tracker))))

(defn with-gource
  "Create & display the animation for a LogStore file."

  [lgs]

  (with-programs [gource]
    (gource "-" "--log-format" "custom" "-e" "0.5" "--stop-at-end"
            "--highlight-users" "--highlight-dirs" "--colour-images"
            {:in (logstore->gource lgs)})))
