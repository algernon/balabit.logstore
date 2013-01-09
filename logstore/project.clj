(defproject com.balabit/logstore "0.1.2"
  :description "syslog-ng PE logstore reader"
  :url "http://algernon.github.com/balabit.logstore"
  :min-lein-version "2.0.0"
  :mailing-list {:name "Syslog-ng users' and developers' mailing list"
                 :archive "http://lists.balabit.hu/pipermail/syslog-ng"
                 :post "syslog-ng@lists.balabit.hu"
                 :subscribe "syslog-ng-request@lists.balabit.hu?subject=subscribe"
                 :unsubscribe "syslog-ng-request@lists.balabit.hu?subject=unsubscribe"}
  :license {:name "Creative Commons Attribution-ShareAlike 3.0"
            :url "http://creativecommons.org/licenses/by-sa/3.0/"
            :distribution :repo}
  :scm {:url "git@github.com:algernon/balabit.logstore.git"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [joda-time/joda-time "2.0"]
                 [org.clojure/algo.monads "0.1.0"]
                 [com.balabit/balabit.blobbity "0.1.1"]
                 [slingshot "0.10.3"]]
  :plugins [[lein-marginalia "0.7.1"]]
  :profiles {:1.5 {:dependencies [[org.clojure/clojure "1.5.0-RC1"]]}}
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :aot [balabit.logstore.exceptions
        balabit.logstore.java]
  :prep-tasks ["compile"]
  :aliases {"with-all-profiles" ["with-profile" "dev:dev,1.5"]
            "docs" ["marg"
                    "src/clj/balabit/logstore.clj"
                    "src/clj/balabit/logstore/sweet.clj"
                    "src/clj/balabit/logstore/codec.clj"
                    "src/clj/balabit/logstore/codec/verify.clj"
                    "src/clj/balabit/logstore/codec/common.clj"
                    "src/clj/balabit/logstore/codec/timestamp.clj"
                    "src/clj/balabit/logstore/codec/xfrm_info.clj"
                    "src/clj/balabit/logstore/codec/chunk.clj"
                    "src/clj/balabit/logstore/codec/chunk/serialization.clj"
                    "src/clj/balabit/logstore/codec/chunk/sweet.clj"
                    "src/clj/balabit/logstore/utils.clj"
                    "src/clj/balabit/logstore/crypto.clj"
                    "src/clj/balabit/logstore/exceptions.clj"
                    "src/clj/balabit/logstore/java.clj"]})
