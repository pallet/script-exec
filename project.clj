(defproject com.palletops/script-exec "0.5.1-SNAPSHOT"
  :description "Functions for executing scripts locally."
  :url "http://palletops.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.palletops/cache-resources "0.1.0"]
                 [com.palletops/local-transport "0.5.0"]
                 [com.palletops/ssh-transport "0.7.0"]
                 [prismatic/schema "0.2.1"]])
