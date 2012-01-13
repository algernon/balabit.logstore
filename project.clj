(defproject balabit.logstore "0.1.0-SNAPSHOT"
  :description "syslog-ng PE logstore reader"
  :aot :all
  :dependencies [
                 [org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [slingshot "0.10.1"]
                 [joda-time/joda-time "2.0"]
                 ]
  :dev-dependencies [
                     [lein-marginalia "0.7.0-SNAPSHOT"]
                     [midje "1.3.1"]
                     [lein-midje "1.0.7"]
                     ]
  )
