(ns balabit.logstore.test.sweet
  (:use [clojure.test])
  (:import (org.joda.time DateTime DateTimeZone))
  (:require [balabit.logstore.sweet :as logstore]))

(defn- with-pruned-records
  [store]

  (assoc store :records (map #(dissoc % :messages :macs) (:records store))))

(deftest uncompressed-unserialized-unencrypted-store
  (testing "Uncompressed, unserialized, unencrypted logstore;"
    (let [store (logstore/from-file "resources/logstores/abc.uncompressed.store")
          meta-data (with-pruned-records (dissoc store :crypto))]
      (testing "meta-data"
        (is (= meta-data {:records (list
                                    {:end-time (DateTime. "2012-01-13T17:10:03.531+01:00"),
                                     :start-time (DateTime. "2012-01-13T17:09:55.630+01:00"),
                                     :chunk-id 0,
                                     :msg-limits {:first-id 1, :last-id 1810},
                                     :type :chunk
                                     :flags [:hash]})})))
      (testing "message availability"
        (is (= (count (logstore/messages store)) 1810))))))

(deftest short-compressed-unserialized-unencrypted-store
  (testing "Compressed, unserialized, unencrypted logstore, with hand-made data;"
    (let [store (logstore/from-file "resources/logstores/short.compressed.store")
          meta-data (with-pruned-records (dissoc store :crypto))]
      (testing "meta-data"
        (is (= meta-data {:records (list
                                    {:end-time (DateTime. "2012-01-13T13:43:58.987+01:00")
                                     :start-time (DateTime. "2012-01-13T13:43:37.812+01:00")
                                     :chunk-id 0
                                     :msg-limits {:first-id 1, :last-id 4}
                                     :type :chunk
                                     :flags [:compressed :hash]})})))

      (testing "messages"
        (is (= (logstore/messages store)
               [{:MESSAGE "Jan 13 13:43:37 localhost This is a test message.\n"}
                {:MESSAGE "Jan 13 13:43:40 localhost And another.\n"}
                {:MESSAGE "Jan 13 13:43:50 localhost Three times' the charm!\n"}
                {:MESSAGE "Jan 13 13:43:58 localhost ...and one more for bonus points.\n"}]))))))

(deftest long-compressed-unserialized-unencrypted-store
  (testing "Compressed, unserialized, unencrypted logstore, with multiple chunks;"
    (let [store (logstore/from-file "resources/logstores/loggen.compressed.store")
          meta-data (with-pruned-records (dissoc store :crypto))]

      (testing "meta-data"
        (is (= (count (:records store)) 9)))

      (testing "messages"
        (is (= (count (logstore/messages store)) 34212))
        (is (= (last (logstore/messages store))
               {:MESSAGE "Jan 11 11:03:39 localhost prg00000[1234]: seq: 0000034211, thread: 0000, runid: 1326276209, stamp: 2012-01-11T11:03:39 PADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADDPADD\n"}))))))

(deftest serialized-uncompressed-unencrypted-store
  (testing "Uncompressed, serialized, unencrypted store;"
    (let [store (logstore/from-file "resources/logstores/serialized.store")
          meta-data (with-pruned-records (dissoc store :crypto))]

      (testing "meta-data"
        (let [records (:records meta-data)]
          (is (= (count records) 1))
          (is (= (:type (first records)) :chunk))
          (is (= (:flags (first records)) [:serialized :hash]))))

      (testing "messages"
        (let [message (first (logstore/messages store))]
          (is (= (dissoc message :meta)
                 {:MESSAGE "This is a test message."
                  :HOST "localhost"
                  :HOST_FROM "localhost"
                  :SOURCE "s_net"
                  :random-thingy "this"}))

          (is (= (dissoc (:meta message)
                         :stamp :recv-stamp :socket)
                 {:tags [:s_tcp :.source.s_net]
                  :severity :notice
                  :facility :user
                  :rcptid 0N})))))))

(deftest timestamped-compressed-serialized-unencrypted-store
  (testing "Timestamped, compressed, serialized, unencrypted store;"
    (let [store (logstore/from-file "resources/logstores/timestamped.store")
          meta-data (with-pruned-records (dissoc store :crypto))]

      (testing "meta-data"
        (let [records (:records meta-data)]
          (is (= (count records)) 2)
          (is (= (:type (first records)) :chunk))
          (is (= (:flags (first records)) [:compressed :serialized :hash]))
        
          (is (= (:type (last records)) :timestamp))))

      (testing "messages"
        (is (= (dissoc (first (logstore/messages store)) :meta)
               {:HOST "10.20.0.26"
                :HOST_FROM "10.20.0.26"
                :LEGACY_MSGHDR "human[1]: "
                :MESSAGE "This is a test message."
                :PROGRAM "human"
                :PID "1"
                :SOURCE "s_s_cmpstamp"
                :.SDATA.timeQuality.isSynced "0"
                :.classifier.class "unknown"}))

        (is (= (dissoc (:meta (first (logstore/messages store)))
                       :stamp :recv-stamp :socket)
               {:tags [:.source.s_s_cmpstamp]
                :severity :notice
                :facility :user}))
        (is (= (-> (logstore/messages store) first :meta :socket :family) :inet4))
        (is (= (-> (logstore/messages store) first :meta :socket :port) 26345))))))
