(defproject com.palletops/script-exec "0.3.6-SNAPSHOT"
  :description "Functions for executing scripts locally."
  :url "http://palletops.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.logging "0.2.0"]
                 [com.palletops/local-transport "0.4.0"]
                 [com.palletops/ssh-transport "0.4.5"]])
