(ns balabit.logstore.test.logstore
  (:use [midje.sweet]
        [balabit.logstore]))

(with-logstore "resources/loggen.store"
  (facts "about logstore meta-data"
         (logstore-header :magic) => "LST4"
         (logstore-header :length) => 183
         (logstore-header :crypto :algo_hash) => "SHA1"
         (logstore-header :crypto :algo_crypt) => "AES-128-CBC"))
