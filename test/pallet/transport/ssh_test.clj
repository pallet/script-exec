(ns pallet.transport.ssh-test
  (:require
   [pallet.common.logging.logutils :as logutils]
   [pallet.transport :as transport]
   [pallet.transport.ssh :as ssh]
   [pallet.utils :as utils])
  (:use
   clojure.test))

(use-fixtures :once (logutils/logging-threshold-fixture))

(defn test-username
  "Function to get test username. This is a function to avoid issues with AOT."
  [] (or (. System getProperty "ssh.username")
         (. System getProperty "user.name")))

(deftest exec-test
  (let [transport (ssh/make-ssh-transport {})
        t-state (transport/open
                 transport
                 {:server "localhost"}
                 {:user (assoc utils/*admin-user*
                          :username (test-username) :no-sudo true)}
                 nil)]
    (testing "Default shell"
      (let [result (transport/exec t-state {:in "ls /; exit $?"} nil)]
        (is (zero? (:exit result)))
        (is (re-find #"bin" (:out result)))))
    (testing "Explicit program"
      (let [result (transport/exec t-state {:execv ["/bin/ls" "/"]} nil)]
        (is (zero? (:exit result)))
        (is (re-find #"bin" (:out result)))))
    (testing "output-f"
      (let [output (atom "")
            result (transport/exec
                    t-state
                    {:in "/bin/ls /; exit $?"}
                    {:output-f (partial swap! output str)})]
        (is (zero? (:exit result)))
        (is (re-find #"bin" (:out result)))
        (is (= (:out result) @output))))
    (testing "Error return"
      (logutils/suppress-logging
       (let [result (transport/exec t-state {:in "this-should-fail"} nil)]
         (is (not (zero? (:exit result)))))))))

(deftest send-test
  (let [transport (ssh/make-ssh-transport {})
        t-state (transport/open
                 transport
                 {:server "localhost"}
                 {:user (assoc utils/*admin-user*
                          :username (test-username) :no-sudo true)}
                 nil)]
    (testing "send"
      (utils/with-temp-file [tmp-src "src"]
        (utils/with-temp-file [tmp-dest "dest"]
          (transport/send t-state (.getPath tmp-src) (.getPath tmp-dest))
          (is (= "src" (slurp tmp-dest))))))
    (testing "send-str"
      (utils/with-temp-file [tmp-dest "dest"]
        (transport/send-str t-state "src" (.getPath tmp-dest))
        (is (= "src" (slurp tmp-dest)))))
    (testing "receive"
      (utils/with-temp-file [tmp-src "src"]
        (utils/with-temp-file [tmp-dest "dest"]
          (transport/receive t-state (.getPath tmp-src) (.getPath tmp-dest))
          (is (= "src" (slurp tmp-dest))))))))
