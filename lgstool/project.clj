(defproject com.balabit/logstore.cli "0.1.2-SNAPSHOT"
  :description "syslog-ng PE logstore reader CLI"
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
                 [com.balabit/logstore "0.1.2-SNAPSHOT"]
                 [org.clojure/tools.cli "0.2.2"]
                 [de.ubercode.clostache/clostache "1.3.1"]
                 [robert/hooke "1.3.0"]
                 [me.raynes/conch "0.4.0"]]
  :plugins [[lein-marginalia "0.7.1"]]
  :source-paths ["src/clj"]
  :profiles {:1.5 {:dependencies [[org.clojure/clojure "1.5.0-RC1"]]}}
  :aliases {"with-all-profiles" ["with-profile" "dev:dev,1.5"],
            "lgstool" ["run" "--"]
            "docs" ["marg"
                    "src/clj/balabit/logstore/cli.clj"
                    "src/clj/balabit/logstore/cli/search_predicates.clj"
                    "src/clj/balabit/logstore/visualisation/gource.clj"]}
  :main balabit.logstore.cli)
