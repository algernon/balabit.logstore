(defproject com.balabit/logstore.bundle "0.1.2-SNAPSHOT"
  :description "syslog-ng PE logstore reader & CLI"
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
  :plugins [[lein-sub "0.2.4"]
            [lein-marginalia "0.7.1"]]
  :profiles {:logstore {:sub ["logstore"]}
             :lgstool {:sub ["lgstool"]}}
  :aliases {"clean" ["with-profile" "logstore,lgstool" "sub" "clean"]
            "install" ["with-profile" "logstore,lgstool" "sub" "install"]
            "deploy" ["with-profile" "logstore,lgstool" "sub" "deploy"]
            "push" ["with-profile" "logstore,lgstool" "sub" "push"]
            "test" ["with-profile" "logstore,lgstool" "sub" "test"]
            "with-all-profiles" ["with-profile" "logstore,lgstool" "sub" "with-profile" "dev:dev,1.5"]

            "lgstool" ["with-profile" "lgstool" "sub" "lgstool"]

            "build-docs" ["with-profile" "dev" "marg"
                          "logstore/src/clj/balabit/logstore.clj"
                          "logstore/src/clj/balabit/logstore/sweet.clj"
                          "logstore/src/clj/balabit/logstore/codec.clj"
                          "logstore/src/clj/balabit/logstore/codec/verify.clj"
                          "logstore/src/clj/balabit/logstore/codec/common.clj"
                          "logstore/src/clj/balabit/logstore/codec/timestamp.clj"
                          "logstore/src/clj/balabit/logstore/codec/xfrm_info.clj"
                          "logstore/src/clj/balabit/logstore/codec/chunk.clj"
                          "logstore/src/clj/balabit/logstore/codec/chunk/serialization.clj"
                          "logstore/src/clj/balabit/logstore/codec/chunk/sweet.clj"
                          "logstore/src/clj/balabit/logstore/utils.clj"
                          "logstore/src/clj/balabit/logstore/crypto.clj"
                          "logstore/src/clj/balabit/logstore/exceptions.clj"
                          "logstore/src/clj/balabit/logstore/java.clj"
                          "lgstool/src/clj/balabit/logstore/cli.clj"
                          "lgstool/src/clj/balabit/logstore/cli/search_predicates.clj"
                          "lgstool/src/clj/balabit/logstore/visualisation/gource.clj"]})
