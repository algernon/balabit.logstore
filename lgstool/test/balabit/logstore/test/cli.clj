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

(deftest inspect
  (testing "the 'inspect' command"
    (let [lgs-fn "../logstore/resources/logstores/sha256.store"
          inspected (cli/lgstool-inspect message-identity lgs-fn)
          parsed (logstore/from-file lgs-fn)]
      (is (= inspected parsed)))))

(deftest search
  (let [lgs-fn "../logstore/resources/logstores/sha256.store"
        o-cat (cli/lgstool-cat message-identity lgs-fn)]
    (testing "the 'search' command"
      (testing "without arguments"
        (let [o-search (cli/lgstool-search message-identity lgs-fn)]
          (is (= o-cat o-search))))

      (testing "the 'tag' predicate"
        (let [o-tag (cli/lgstool-search message-identity lgs-fn
                                        "(tag :s_tcp)")]
          (is (= o-cat o-tag)))

        (let [o-tag (cli/lgstool-search message-identity lgs-fn
                                        "(tag :no-such-tag)")]
          (is (empty? o-tag))))

      (testing "the 'severity' predicate"
        (let [o-sev (cli/lgstool-search message-identity lgs-fn
                                        "(severity :informational)")]
          (is (= o-cat o-sev)))

        (let [o-sev (cli/lgstool-search message-identity lgs-fn
                                        "(severity :error)")]
          (is (empty? o-sev))))

      (testing "the 'facility' predicate"
        (let [o-fac (cli/lgstool-search message-identity lgs-fn
                                        "(facility :auth)")]
          (is (= o-cat o-fac)))

        (let [o-fac (cli/lgstool-search message-identity lgs-fn
                                        "(facility :mail)")]
          (is (empty? o-fac))))

      (testing "the '===' predicate"
        (let [o-match (cli/lgstool-search message-identity lgs-fn
                                          "(=== :HOST_FROM \"localhost\")")]
          (is (= (count o-match) 3)))

        (let [o-match (cli/lgstool-search message-identity lgs-fn
                                          "(=== :SOCKET :family :inet4)")]
          (is (= (count o-match) 3)))

        (let [o-match (cli/lgstool-search message-identity lgs-fn
                                          "(=== :random-thingy :foobar)")]
          (is (empty? o-match))))

      (testing "the 're-match' / '?=' predicates"
        (let [o-re-match (cli/lgstool-search message-identity lgs-fn
                                             "(re-match #\"seq: [0-9]+\")")
              o-re-alias (cli/lgstool-search message-identity lgs-fn
                                             "(?= #\"seq: [0-9]+\")")]
          (is (= (count o-re-match) 3))
          (is (= o-re-match o-re-alias)))

        (let [o-re-match (cli/lgstool-search message-identity lgs-fn
                                             "(?= :SOURCE #\"^s_\")")]
          (is (= (count o-re-match) 3)))

        (let [o-re-match (cli/lgstool-search message-identity lgs-fn
                                             "(?= :PROGRAM \"prg\")")]
          (is (= (count o-re-match) 3)))

        (let [o-re-match (cli/lgstool-search message-identity lgs-fn
                                             "(?= :PROGRAM \"foobar\")")]
          (is (empty? o-re-match))))

      (testing "the 'date' related predicates"
        (let [lgs-fn "../logstore/resources/logstores/timestamped.store"]

          (testing "'before'"
            (let [o-before-2011 (cli/lgstool-search message-identity lgs-fn
                                                    "(before \"2011-11-16T05:09:42.161+01:00\")")
                  o-before-2012 (cli/lgstool-search message-identity lgs-fn
                                                    "(before \"2012-01-01\")")
                  o-before-2010 (cli/lgstool-search message-identity lgs-fn
                                                    "(before \"2010-01-01\")")]
              (is (= (count o-before-2011) 1))
              (is (= (count o-before-2012) 2))
              (is (empty? o-before-2010))))

          (testing "'after'"
            (let [o-after-2011 (cli/lgstool-search message-identity lgs-fn
                                                   "(after \"2011-11-16T05:09:42.160+01:00\")")
                  o-after-2012 (cli/lgstool-search message-identity lgs-fn
                                                   "(after \"2012-01-01\")")
                  o-after-2010 (cli/lgstool-search message-identity lgs-fn
                                                   "(after \"2010-01-01\")")]
              (is (= (count o-after-2011) 1))
              (is (= (count o-after-2010) 2))
              (is (empty? o-after-2012))))

          (testing "'on'"
            (let [o-on-1 (cli/lgstool-search message-identity lgs-fn
                                             "(on \"2011-11-16T05:09:42.161+01:00\")")
                  o-on-2 (cli/lgstool-search message-identity lgs-fn
                                             "(on \"2011-11-16T05:09:13.562+01:00\")")
                  o-on-3 (cli/lgstool-search message-identity lgs-fn
                                             "(on \"2012-01-01\")")]
              (is (= (count o-on-1) 1))
              (is (= (count o-on-2) 1))
              (is (empty? o-on-3))
              (is (not (= o-on-1 o-on-2)))))))

      (testing "multiple predicates"
        (let [o-multi (cli/lgstool-search message-identity lgs-fn
                                          "(=== :random-thingy \"this\")"
                                          "(tag :s_tcp)"
                                          "(?= \"00[12],\")")
              o-none (cli/lgstool-search message-identity lgs-fn
                                         "(=== :random-thingy \"this\")"
                                         "(tag :s_tcp)"
                                         "(?= \"foobar\")")]
          (is (= (count o-multi) 2))
          (is (empty? o-none)))))))

(deftest main
  (testing "the '-main' function"
    (is (= (with-out-str (cli/lgstool-help))
           (with-out-str (cli/-main "help"))))

    (is (= (with-out-str (cli/lgstool-help))
           (with-out-str (cli/-main))))

    (is (= (str "Unknown command: no-such-command\n"
                (with-out-str (cli/lgstool-help)))
           (with-out-str (cli/-main "no-such-command"))))))
