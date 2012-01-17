(ns balabit.logstore.test.logstore
  (:use [midje.sweet]
        [balabit.logstore]))

; Compressed, unserialized, unencrypted logstore, with uniform message length.
(with-logstore "resources/logstores/loggen.compressed.store"
  (facts "about logstore meta-data"
         (logstore-header :magic) => "LST4"
         (logstore-header :crypto :algo-hash) => "SHA1"
         (logstore-header :crypto :algo-crypt) => "AES-128-CBC")

  (fact "about the number of records in the logstore"
        (count (logstore-records)) => 9)

  (with-logstore-record 1
    (facts "about a specific record within the logstore"
           (logstore-record :header :offset) => 13878
           (logstore-record :header :size) => 14079
           (logstore-record :header :type) => :chunk
           (logstore-record :header :flags) => [:compressed]
           (count (logstore-record :messages)) => 4161
           (logstore-record :first-msgid) => 4162
           (logstore-record :last-msgid) => 8322
           (logstore-record :chunk-id) => 1
           (logstore-record :xfrm-offset) => 0
           (logstore-record :flags) => [:hash]
           (nth (logstore-record :messages) 0) => (contains "PADDPADDPADD"))

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

; Short, compressed, unserialized and unencrypted logstore, with hand-made data.
(with-logstore "resources/logstores/short.compressed.store"
  (facts "about a short, compressed logstore's meta-data"
         (logstore-header :magic) => "LST4"
         (count (logstore-records)) => 1)

  (with-logstore-record 0
    (facts "about the single record in the logstore"
           (logstore-record :header :offset) => 187
           (logstore-record :header :type) => :chunk
           (logstore-record.compressed?) => true

           (nth (logstore-record :messages) 0) => (contains "test message")
           (count (logstore-record :messages)) => 4)))

; Uncompressed, unserialized, unencrypted logstore
(with-logstore "resources/logstores/abc.uncompressed.store"
  (facts "about an uncompressed logstore"
         (logstore-header :magic) => "LST4"
         (count (logstore-records)) => 1)

  (with-logstore-record 0
    (facts "about a single record in the logstore"
           (logstore-record :header :type) => :chunk
           (logstore-record.compressed?) => false

           (nth (logstore-record :messages) 0) => (contains "localhost 1")
           (count (logstore-record :messages)) => 1810)))

; Compressed, serialised, unencrypted, but timestamped logstore
(with-logstore "resources/logstores/timestamped.store"
  (facts "about a timestamped, serialized logstore"
         (logstore-header :magic) => "LST4"
         (count (logstore-records)) => 2)

  (with-logstore-record 1
    (facts "about a timestamp record"
           (logstore-record :header :type) => :timestamp
           (logstore-record :header :flags) => []
           (logstore-record :chunk-id) => 0
           (.limit (logstore-record :timestamp)) => 2492)))
