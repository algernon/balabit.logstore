(ns balabit.logstore.codec.chunk.sweet
  "### Little helpers for dealing with chunk flags"
  
  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Creative Commons Attribution-ShareAlike 3.0"
              :url "http://creativecommons.org/licenses/by-sa/3.0/"}}

  (:use [balabit.logstore.utils]))

(defn compressed?
  "Given a chunk, check if the :compressed flag is set."
  [chunk]

  (flag-set? chunk :compressed))

(defn serialized?
  "Given a chunk, check if the :serialized flag is set."
  [chunk]

  (flag-set? chunk :serialized))
