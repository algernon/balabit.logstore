(defproject com.balabit/logstore "0.0.1-SNAPSHOT"
  :description "syslog-ng PE logstore reader"
  :url "http://algernon.github.com/balabit.logstore"
  :min-lein-version "2.0.0"
  :mailing-list {:name "Syslog-ng users' and developers' mailing list"
                 :archive "http://lists.balabit.hu/pipermail/syslog-ng"
                 :post "syslog-ng@lists.balabit.hu"
                 :subscribe "syslog-ng-request@lists.balabit.hu?subject=subscribe"
                 :unsubscribe "syslog-ng-request@lists.balabit.hu?subject=unsubscribe"}
  :license {:name "GNU General Public License - v3"
            :url "http://www.gnu.org/licenses/gpl.txt"
            :distribution :repo}
  :aot :all
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [slingshot "0.10.1"]
                 [joda-time/joda-time "2.0"]
                 [gloss "0.2.1-rc1"]]
  :profiles {:dev
             {:dependencies
              [[midje "1.3.1" :exclusions [org.clojure/clojure]]]}}
  :plugins [[lein-marginalia "0.7.0"]
            [lein-midje "2.0.0-SNAPSHOT"]
            [lein-clojars "0.8.0"]
            [lein-exec "0.2.0"]])
