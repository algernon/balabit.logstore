(ns balabit.logstore.test.sweet
  (:use [clojure.test])
  (:import (org.joda.time DateTime DateTimeZone))
  (:require [balabit.logstore.sweet :as logstore]))

(defn- with-pruned-records
  [store]

  (assoc store :records (map #(dissoc % :messages) (:records store))))

(deftest uncompressed-unserialized-unencrypted-store
  (testing "Uncompressed, unserialized, unencrypted logstore;"
    (let [store (logstore/from-file "resources/logstores/abc.uncompressed.store")
          meta-data (with-pruned-records store)]
      (testing "meta-data"
        (is (= meta-data {:records (list
                                    {:end-time (DateTime. "2012-01-13T17:10:03.531+01:00"),
                                     :start-time (DateTime. "2012-01-13T17:09:55.630+01:00"),
                                     :chunk-id 0,
                                     :msg-limits {:first-id 1, :last-id 1810},
                                     :macs {:chunk-hmac "a0501e2279d6232be208420ca62dd0fcdab69d56"
                                            :file-mac "c49ef740fd3a31c61b6a8ebb0d158cf1f924b8a5"}
                                     :type :chunk
                                     :flags [:hash]})
                          :crypto {:file-mac "c49ef740fd3a31c61b6a8ebb0d158cf1f924b8a5"
                                   :algo {:crypt "AES-128-CBC"
                                          :hash "SHA1"}}})))

      (testing "checksums"
        (is (= (-> store :crypto :file-mac)
               (-> store :records (last) :macs :file-mac))))

      (testing "message availability"
        (is (= (count (logstore/messages store)) 1810))))))

(deftest short-compressed-unserialized-unencrypted-store
  (testing "Compressed, unserialized, unencrypted logstore, with hand-made data;"
    (let [store (logstore/from-file "resources/logstores/short.compressed.store")
          meta-data (with-pruned-records store)]
      (testing "meta-data"
        (is (= meta-data {:records (list
                                    {:end-time (DateTime. "2012-01-13T13:43:58.987+01:00")
                                     :start-time (DateTime. "2012-01-13T13:43:37.812+01:00")
                                     :chunk-id 0
                                     :msg-limits {:first-id 1, :last-id 4}
                                     :macs {:chunk-hmac "466a2d486513ceb282341be100b8f0f90a39800b"
                                            :file-mac "cf49d66468040421b043774c1fe0875d0589c9fb"}
                                     :type :chunk
                                     :flags [:compressed :hash]})
                          :crypto {:file-mac "cf49d66468040421b043774c1fe0875d0589c9fb"
                                   :algo {:crypt "AES-128-CBC"
                                          :hash "SHA1"}}})))

      (testing "checksums"
        (is (= (-> store :crypto :file-mac)
               (-> store :records (last) :macs :file-mac))))

      (testing "messages"
        (is (= (logstore/messages store)
               [{:MESSAGE "Jan 13 13:43:37 localhost This is a test message.\n"}
                {:MESSAGE "Jan 13 13:43:40 localhost And another.\n"}
                {:MESSAGE "Jan 13 13:43:50 localhost Three times' the charm!\n"}
                {:MESSAGE "Jan 13 13:43:58 localhost ...and one more for bonus points.\n"}]))))))

(deftest long-compressed-unserialized-unencrypted-store
  (testing "Compressed, unserialized, unencrypted logstore, with multiple chunks;"
    (let [store (logstore/from-file "resources/logstores/loggen.compressed.store")
          meta-data (with-pruned-records store)]

      (testing "meta-data"
        (is (= (count (:records store)) 9)))

      (testing "messages"
        (is (= (count (logstore/messages store)) 34212))
        (is (= (last (logstore/messages store))
               {:MESSAGE "Jan 11 11:03:39 localhost prg00000[1234]: seq: 0000034211, thread: 0000, runid: 1326276209, stamp: 2012-01-11T11:03:39 PADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADD\n"})))

      (testing "checksums"
        (is (= (-> store :crypto :file-mac)
               (-> store :records (last) :macs :file-mac)))))))

(deftest serialized-uncompressed-unencrypted-store
  (testing "Uncompressed, serialized, unencrypted store;"
    (let [store (logstore/from-file "resources/logstores/serialized.store")
          meta-data (with-pruned-records store)]

      (testing "meta-data"
        (let [records (:records meta-data)]
          (is (= (count records) 1))
          (is (= (:type (first records)) :chunk))
          (is (= (:flags (first records)) [:serialized :hash]))))

      (testing "messages"
        (let [message (first (logstore/messages store))]
          (is (= (dissoc message :TIMESTAMP :RECV_TIMESTAMP :SOCKET)
                 {:MESSAGE "This is a test message."
                  :HOST "localhost"
                  :HOST_FROM "localhost"
                  :SOURCE "s_net"
                  :FACILITY :user
                  :SEVERITY :notice
                  :RCPTID 0N
                  :TAGS [:s_tcp :.source.s_net]
                  :random-thingy "this"}))))

      (testing "checksums"
        (is (= (-> store :crypto :file-mac)
               (-> store :records (last) :macs :file-mac)))))))

(deftest timestamped-compressed-serialized-unencrypted-store
  (testing "Timestamped, compressed, serialized, unencrypted store;"
    (let [store (logstore/from-file "resources/logstores/timestamped.store")
          meta-data (with-pruned-records store)]

      (testing "meta-data"
        (let [records (:records meta-data)]
          (is (= (count records)) 2)
          (is (= (:type (first records)) :chunk))
          (is (= (:flags (first records)) [:compressed :serialized :hash]))

          (is (= (:type (last records)) :timestamp))))

      (testing "messages"
        (is (= (dissoc (first (logstore/messages store)) :TIMESTAMP :RECV_TIMESTAMP :SOCKET)
               {:HOST "10.20.0.26"
                :HOST_FROM "10.20.0.26"
                :LEGACY_MSGHDR "human[1]: "
                :MESSAGE "This is a test message."
                :PROGRAM "human"
                :PID "1"
                :SOURCE "s_s_cmpstamp"
                :.SDATA.timeQuality.isSynced "0"
                :.classifier.class "unknown"
                :TAGS [:.source.s_s_cmpstamp]
                :SEVERITY :notice
                :FACILITY :user}))

        (is (= (-> (logstore/messages store) first :SOCKET :family) :inet4))
        (is (= (-> (logstore/messages store) first :SOCKET :port) 26345)))

      (testing "timestamp"
        (let [records (:records meta-data)]
          (is (= (:chunk-id (last records) 0)))
          (is (= (.limit (:timestamp (last records))) 2492))))

      (testing "checksums"
        (is (= (-> store :crypto :file-mac)
               (-> store :records (first) :macs :file-mac)))))))

(deftest sha256-store
  (testing "LogStore with custom (SHA256) digest algorithm"
    (let [store (logstore/from-file "resources/logstores/sha256.store")
          meta-data (dissoc store :records)]

      (testing "meta-data"
        (is (= meta-data
               {:crypto {:file-mac
                         "7e1493fc90f4bfe0337fa6c19668d7e4b51d358ea92052f74a1f73cf6926f8ed",
                         :algo {:crypt "AES-256-CBC", :hash "SHA256"}}})))

      (testing "checksums"
        (is (= (-> store :crypto :file-mac)
               (-> store :records (first) :macs :file-mac)))))))
