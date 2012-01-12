(ns balabit.logstore.test.logstore
  (:use [midje.sweet]
        [balabit.logstore]))

(with-logstore "resources/loggen.store"
  (facts "about logstore meta-data"
         (logstore-header :magic) => "LST4"
         (logstore-header :length) => 183
         (logstore-header :crypto :algo_hash) => "SHA1"
         (logstore-header :crypto :algo_crypt) => "AES-128-CBC")

  (fact "about the number of records in the logstore"
        (count (logstore-records)) => 9)

  (with-logstore-record 1
    (facts "about a specific record within the logstore"
           (logstore-record :header :offset) => 13878
           (logstore-record :header :size) => 14079
           (logstore-record :header :type) => :chunk
           (logstore-record :header :flags) => [:compressed]
           (.limit (logstore-record :data)) => 14073)))
