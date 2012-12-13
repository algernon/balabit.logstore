(defproject com.balabit/logstore "0.1.0-SNAPSHOT"
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
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [joda-time/joda-time "2.0"]
                 [org.clojure/tools.cli "0.2.2"]
                 [com.balabit/balabit.blobbity "0.1.0"]
                 [de.ubercode.clostache/clostache "1.3.1"]]
  :profiles {:dev {:plugins [[lein-marginalia "0.7.1"]]}}
  :aliases {"with-all-profiles" ["with-profile" "dev"],
            "lgstool" ["run" "--"]
            "build-docs" ["with-profile" "dev" "marg"
                          "src/balabit/logstore.clj"
                          "src/balabit/logstore/sweet.clj"
                          "src/balabit/logstore/codec.clj"
                          "src/balabit/logstore/codec/common.clj"
                          "src/balabit/logstore/codec/timestamp.clj"
                          "src/balabit/logstore/codec/chunk.clj"
                          "src/balabit/logstore/codec/chunk/serialization.clj"
                          "src/balabit/logstore/codec/chunk/sweet.clj"
                          "src/balabit/logstore/utils.clj"
                          "src/balabit/logstore/cli.clj"
                          "src/balabit/logstore/cli/search_predicates.clj"]}
  :main balabit.logstore.cli)
