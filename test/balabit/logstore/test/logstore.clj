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
           (.limit (logstore-record :data)) => 14073)

    (facts "about record flag access"
           (logstore-record.compressed?) => true
           (logstore-record.encrypted?) => false
           (logstore-record.broken?) => false
           (logstore-record.serialized?) => false))

  (let [rec (logstore-nth 1)]
    (facts "about record flag access, with an explicit record"
           (logstore-record.compressed? rec) => true
           (logstore-record.encrypted? rec) => false
           (logstore-record.broken? rec) => false
           (logstore-record.serialized? rec) => false)))
