(ns pallet.transport.ssh-test
  (:require
   [pallet.common.filesystem :as filesystem]
   [pallet.common.logging.logutils :as logutils]
   [pallet.transport :as transport]
   [pallet.transport.ssh :as ssh])
  (:use
   clojure.test))

(use-fixtures :once (logutils/logging-threshold-fixture))

(defn default-private-key-path
  "Return the default private key path."
  []
  (str (System/getProperty "user.home") "/.ssh/id_rsa"))

(defn default-public-key-path
  "Return the default public key path"
  []
  (str (System/getProperty "user.home") "/.ssh/id_rsa.pub"))

(defn test-username
  "Function to get test username. This is a function to avoid issues with AOT."
  [] (or (. System getProperty "ssh.username")
         (. System getProperty "user.name")))

(deftest exec-test
  (let [transport (ssh/make-ssh-transport {})
        t-state (transport/open
                 transport
                 {:server "localhost"}
                 {:user {:private-key-path (default-private-key-path)
                         :public-key-path (default-public-key-path)
                         :username (test-username)
                         :no-sudo true}}
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
                 {:user {:private-key-path (default-private-key-path)
                         :public-key-path (default-public-key-path)
                         :username (test-username)
                         :no-sudo true}}
                 nil)]
    (testing "send"
      (filesystem/with-temp-file [tmp-src "src"]
        (filesystem/with-temp-file [tmp-dest "dest"]
          (transport/send t-state (.getPath tmp-src) (.getPath tmp-dest))
          (is (= "src" (slurp tmp-dest))))))
    (testing "send-str"
      (filesystem/with-temp-file [tmp-dest "dest"]
        (transport/send-str t-state "src" (.getPath tmp-dest))
        (is (= "src" (slurp tmp-dest)))))
    (testing "receive"
      (filesystem/with-temp-file [tmp-src "src"]
        (filesystem/with-temp-file [tmp-dest "dest"]
          (transport/receive t-state (.getPath tmp-src) (.getPath tmp-dest))
          (is (= "src" (slurp tmp-dest))))))))
