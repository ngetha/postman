(defproject postman "0.1.0-SNAPSHOT"
  :description "postman - realiable money gateway as the postman"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.8"]
                 [http-kit "2.1.16"]
                 [org.clojure/java.jdbc "0.3.2"]
                 [org.clojure/tools.logging "0.2.6"]
                 [c3p0/c3p0 "0.9.1.2"]
                 [org.postgresql/postgresql "9.3-1101-jdbc41"]
                 [org.clojure/data.json "0.2.4"]
                 [log4j "1.2.17"]
                 [joda-time/joda-time "2.5"]
                 [ring/ring-defaults "0.1.1"]
                 [ring "1.3.1"]
                 [org.clojure/data.json "0.2.5"]]
  :main ^:skip-aot postman.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
