(ns balabit.logstore.test.cli
  (:use [clojure.test])
  (:require [balabit.logstore.cli :as cli]
            [balabit.logstore.sweet :as logstore]))

(deftest help
  (testing "the 'help' command"
    (let [help-output (with-out-str (cli/help))]
      (is (.. #^String help-output (startsWith "Usage: lgstool <command> [options] <filename>"))))))

(deftest cat
  (testing "the 'cat' command"
    (testing "without template"
      (let [o (with-out-str (cli/cat "../logstore/resources/logstores/short.compressed.store"))]
        (is (= o (str "{:MESSAGE \"Jan 13 13:43:37 localhost This is a test message.\\n\"}\n"
                      "{:MESSAGE \"Jan 13 13:43:40 localhost And another.\\n\"}\n"
                      "{:MESSAGE \"Jan 13 13:43:50 localhost Three times' the charm!\\n\"}\n"
                      "{:MESSAGE \"Jan 13 13:43:58 localhost ...and one more for bonus points.\\n\"}\n")))))

    (testing "with template"
      (let [o (with-out-str (cli/cat "../logstore/resources/logstores/serialized.store"
                                     "-t" "{{TIMESTAMP}} {{HOST}} [{{random-thingy}}] {{SOCKET.address}} {{MESSAGE}}"))]
        (is (= o "2012-12-12T22:46:53.131+01:00 localhost [this] /127.0.0.1 This is a test message.\n"))))))

(deftest tail
  (testing "the 'tail' command"
    (testing "with defaults"
      (let [o (with-out-str (cli/tail "../logstore/resources/logstores/abc.uncompressed.store"))]
        (is (= o (str "{:MESSAGE \"Jan 13 17:10:03 localhost 1801\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:10:03 localhost 1802\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:10:03 localhost 1803\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:10:03 localhost 1804\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:10:03 localhost 1805\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:10:03 localhost 1806\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:10:03 localhost 1807\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:10:03 localhost 1808\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:10:03 localhost 1809\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:10:03 localhost 1810\\n\"}\n")))))

    (testing "with -n 2"
      (let [o (with-out-str (cli/tail "../logstore/resources/logstores/abc.uncompressed.store"
                                      "-n" "2"))]
        (is (= o (str "{:MESSAGE \"Jan 13 17:10:03 localhost 1809\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:10:03 localhost 1810\\n\"}\n")))))

    (testing "with -n +1807"
      (let [o (with-out-str (cli/tail "../logstore/resources/logstores/abc.uncompressed.store"
                                      "-n" "+1807"))]
        (is (= o (str "{:MESSAGE \"Jan 13 17:10:03 localhost 1808\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:10:03 localhost 1809\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:10:03 localhost 1810\\n\"}\n")))))))

(deftest head
  (testing "the 'head' command"
    (testing "with defaults"
      (let [o (with-out-str (cli/head "../logstore/resources/logstores/abc.uncompressed.store"))]
        (is (= o (str "{:MESSAGE \"Jan 13 17:09:55 localhost 1\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:09:55 localhost 2\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:09:55 localhost 3\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:09:55 localhost 4\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:09:55 localhost 5\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:09:55 localhost 6\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:09:55 localhost 7\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:09:55 localhost 8\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:09:55 localhost 9\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:09:55 localhost 10\\n\"}\n")))))

    (testing "with -n 2"
      (let [o (with-out-str (cli/head "../logstore/resources/logstores/abc.uncompressed.store"
                                      "-n" "2"))]
        (is (= o (str "{:MESSAGE \"Jan 13 17:09:55 localhost 1\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:09:55 localhost 2\\n\"}\n")))))

    (testing "with -n -1807"
      (let [o (with-out-str (cli/head "../logstore/resources/logstores/abc.uncompressed.store"
                                      "-n" "-1807"))]
        (is (= o (str "{:MESSAGE \"Jan 13 17:09:55 localhost 1\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:09:55 localhost 2\\n\"}\n"
                      "{:MESSAGE \"Jan 13 17:09:55 localhost 3\\n\"}\n")))))))

(deftest random
  (testing "the 'random' command"
    (let [lgs-fn "../logstore/resources/logstores/short.compressed.store"
          o (read-string (with-out-str (cli/random lgs-fn)))
          m (logstore/from-file lgs-fn)]
      (is (some #{o} (logstore/messages m))))))

(deftest main
  (testing "the '-main' function"
    (is (= (with-out-str (cli/help))
           (with-out-str (cli/-main "help"))))

    (is (= (with-out-str (cli/help))
           (with-out-str (cli/-main))))

    (is (= (with-out-str (cli/help))
           (with-out-str (cli/-main "no-such-command"))))))
