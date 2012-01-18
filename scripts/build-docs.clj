#! /usr/bin/lein exec
;; Usage: lein exec scripts/build-docs.clj

(use 'marginalia.core)

(def public-docs
  ["../logstore", "examples"])

(def devel-docs
  ["core", "core/errors", "core/utils",
   "core/file",
   "core/record/common", "core/record",
   "core/record/chunk", "core/record/timestamp"])

(defn fiddle-path
  [path] (str "src/balabit/logstore/" path ".clj"))

(binding [marginalia.html/*resources* ""]
  (run-marginalia
   (into ["-f" "public-api.html"]
         (map fiddle-path public-docs)))
  (run-marginalia
   (into ["-f" "developer-api.html"]
         (map fiddle-path (into devel-docs public-docs)))))
