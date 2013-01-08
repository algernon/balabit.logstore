(ns balabit.logstore.test.cli
  (:use [clojure.test])
  (:require [balabit.logstore.cli :as cli]
            [balabit.logstore.sweet :as logstore]))

(defn message-identity
  "A simple function that acts as a 'printer' for the logstore.cli
  functions: it ignores the template, and returns the message as-is."

  [_ message]

  message)

(deftest help
  (testing "the 'help' command"
    (let [help-output (with-out-str (cli/lgstool-help))]
      (is (.. #^String help-output (startsWith "Usage: lgstool <command> [options] <filename>"))))))

(deftest cat
  (testing "the 'cat' command"
    (testing "without template"
      (let [o (cli/lgstool-cat message-identity
                               "../logstore/resources/logstores/short.compressed.store")]
        (is (= o (list {:MESSAGE "Jan 13 13:43:37 localhost This is a test message.\n"}
                       {:MESSAGE "Jan 13 13:43:40 localhost And another.\n"}
                       {:MESSAGE "Jan 13 13:43:50 localhost Three times' the charm!\n"}
                       {:MESSAGE "Jan 13 13:43:58 localhost ...and one more for bonus points.\n"})))))

    (testing "with template"
      (let [o (with-out-str (dorun (cli/lgstool-cat cli/print-message
                                                    "../logstore/resources/logstores/serialized.store"
                                                    "-t" "{{TIMESTAMP}} {{HOST}} [{{random-thingy}}] {{SOCKET.address}} {{MESSAGE}}")))]
        (is (= o "2012-12-12T22:46:53.131+01:00 localhost [this] /127.0.0.1 This is a test message.\n"))))))

(deftest tail
  (testing "the 'tail' command"
    (testing "with defaults"
      (let [o (cli/lgstool-tail message-identity
                                "../logstore/resources/logstores/abc.uncompressed.store")]
        (is (= o (list {:MESSAGE "Jan 13 17:10:03 localhost 1801\n"}
                       {:MESSAGE "Jan 13 17:10:03 localhost 1802\n"}
                       {:MESSAGE "Jan 13 17:10:03 localhost 1803\n"}
                       {:MESSAGE "Jan 13 17:10:03 localhost 1804\n"}
                       {:MESSAGE "Jan 13 17:10:03 localhost 1805\n"}
                       {:MESSAGE "Jan 13 17:10:03 localhost 1806\n"}
                       {:MESSAGE "Jan 13 17:10:03 localhost 1807\n"}
                       {:MESSAGE "Jan 13 17:10:03 localhost 1808\n"}
                       {:MESSAGE "Jan 13 17:10:03 localhost 1809\n"}
                       {:MESSAGE "Jan 13 17:10:03 localhost 1810\n"})))))

    (testing "with -n 2"
      (let [o (cli/lgstool-tail message-identity
                                "../logstore/resources/logstores/abc.uncompressed.store"
                                "-n" "2")]
        (is (= o (list {:MESSAGE "Jan 13 17:10:03 localhost 1809\n"}
                       {:MESSAGE "Jan 13 17:10:03 localhost 1810\n"})))))

    (testing "with -n +1807"
      (let [o (cli/lgstool-tail message-identity
                                "../logstore/resources/logstores/abc.uncompressed.store"
                                "-n" "+1807")]
        (is (= o (list {:MESSAGE "Jan 13 17:10:03 localhost 1808\n"}
                       {:MESSAGE "Jan 13 17:10:03 localhost 1809\n"}
                       {:MESSAGE "Jan 13 17:10:03 localhost 1810\n"})))))))

(deftest head
  (testing "the 'head' command"
    (testing "with defaults"
      (let [o (cli/lgstool-head message-identity
                                "../logstore/resources/logstores/abc.uncompressed.store")]
        (is (= o (list {:MESSAGE "Jan 13 17:09:55 localhost 1\n"}
                       {:MESSAGE "Jan 13 17:09:55 localhost 2\n"}
                       {:MESSAGE "Jan 13 17:09:55 localhost 3\n"}
                       {:MESSAGE "Jan 13 17:09:55 localhost 4\n"}
                       {:MESSAGE "Jan 13 17:09:55 localhost 5\n"}
                       {:MESSAGE "Jan 13 17:09:55 localhost 6\n"}
                       {:MESSAGE "Jan 13 17:09:55 localhost 7\n"}
                       {:MESSAGE "Jan 13 17:09:55 localhost 8\n"}
                       {:MESSAGE "Jan 13 17:09:55 localhost 9\n"}
                       {:MESSAGE "Jan 13 17:09:55 localhost 10\n"})))))

    (testing "with -n 2"
      (let [o (cli/lgstool-head message-identity
                                "../logstore/resources/logstores/abc.uncompressed.store"
                                "-n" "2")]
      (is (= o (list {:MESSAGE "Jan 13 17:09:55 localhost 1\n"}
                     {:MESSAGE "Jan 13 17:09:55 localhost 2\n"})))))

    (testing "with -n -1807"
      (let [o (cli/lgstool-head message-identity
                                "../logstore/resources/logstores/abc.uncompressed.store"
                                "-n" "-1807")]
        (is (= o (list {:MESSAGE "Jan 13 17:09:55 localhost 1\n"}
                       {:MESSAGE "Jan 13 17:09:55 localhost 2\n"}
                       {:MESSAGE "Jan 13 17:09:55 localhost 3\n"})))))))

(deftest random
  (testing "the 'random' command"
    (let [lgs-fn "../logstore/resources/logstores/short.compressed.store"
          o (cli/lgstool-random message-identity lgs-fn)
          m (logstore/from-file lgs-fn)]
      (is (some #{o} (logstore/messages m))))))

(deftest main
  (testing "the '-main' function"
    (is (= (with-out-str (cli/lgstool-help))
           (with-out-str (cli/-main "help"))))

    (is (= (with-out-str (cli/lgstool-help))
           (with-out-str (cli/-main))))

    (is (= (with-out-str (cli/lgstool-help))
           (with-out-str (cli/-main "no-such-command"))))))
