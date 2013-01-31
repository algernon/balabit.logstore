(defproject com.balabit/logstore.bundle "0.2.0-SNAPSHOT"
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
  :plugins [[lein-sub "0.2.4"]]
  :profiles {:logstore {:sub ["logstore"]}
             :lgstool {:sub ["lgstool"]}}
  :aliases {"clean" ["with-profile" "logstore,lgstool" "sub" "clean"]
            "install" ["with-profile" "logstore,lgstool" "sub" "install"]
            "deploy" ["with-profile" "logstore,lgstool" "sub" "deploy"]
            "push" ["with-profile" "logstore,lgstool" "sub" "push"]
            "compile" ["with-profile" "logstore,lgstool" "sub" "compile"]
            "test" ["with-profile" "logstore,lgstool" "sub" "test"]
            "with-all-profiles" ["with-profile" "logstore,lgstool" "sub" "with-profile" "dev:dev,1.5"]

            "lgstool" ["with-profile" "lgstool" "sub" "lgstool"]
            "docs" ["with-profile" "logstore,lgstool" "sub" "docs"]})
