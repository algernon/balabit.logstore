(ns balabit.logstore.docs
  "Helper functions to generate documentation from the
   balabit.logstore sources."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "GNU General Public License - v3"
              :url "http://www.gnu.org/licenses/gpl.txt"}}

  (:gen-class)
  (:use marginalia.core))

(def public-docs #^{:private true}
  ["../logstore", "examples"])

(def devel-docs #^{:private true}
  ["core", "core/errors", "core/utils",
   "core/file",
   "core/record/common", "core/record",
   "core/record/chunk", "core/record/timestamp"])

(defn- fiddle-path
  [path]
  (str "src/balabit/logstore/" path ".clj"))

(defn generate-docs
  "Generate public and developer API docs from the balabit.logstore
  sources. This function assumes the sources are available as in the
  git repository."
  []

  (binding [marginalia.html/*resources* ""]
    (run-marginalia
     (into ["-f" "public-api.html"]
           (map fiddle-path public-docs)))
    (run-marginalia
     (into ["-f" "developer-api.html"]
           (map fiddle-path (into devel-docs public-docs))))))
