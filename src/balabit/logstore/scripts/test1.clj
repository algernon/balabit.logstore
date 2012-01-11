(ns balabit.logstore.scripts.test1
  (:require [balabit.logstore.core :as lgs]))

(defn main [& args]
  (let [file (or (first args) "resources/loggen.store")
        store (lgs/lst-open file)
        header (:header store)]
    (println "File:" file)
    (println " Magic:" (:magic header))
    (println " ----------------------------")
    (println " Crypto:")
    (println "  Algo: hash:" (:algo_hash (:crypto header)) "crypt:" (:algo_crypt (:crypto header)))))
