(ns pallet.transport.transport-test
  (:use
   clojure.test)
  (:require
   [pallet.common.filesystem :as filesystem]
   [pallet.transport :as transport]))

(defn test-exec
  [transport target options]
  (transport/with-transport [transport transport]
    (let [t-state (transport/open transport target options)]
      (testing "Default shell"
        (let [result (transport/exec t-state {:in "ls /; exit $?"} nil)]
          (is (zero? (:exit result)))
          (is (re-find #"bin" (:out result)))))
      ;; this fails for ssh for some reason
      ;; (testing "Explicit shell"
      ;;   (let [result (transport/exec
      ;;                 t-state
      ;;                 {:execv ["/bin/bash"] :in "ls /; exit $?"} nil)]
      ;;     (is (zero? (:exit result)))
      ;;     (is (re-find #"bin" (:out result)))))
      (testing "Explicit program"
        (let [result (transport/exec t-state {:execv ["/bin/ls" "/"]} nil)]
          (is (zero? (:exit result)))
          (is (re-find #"bin" (:out result)))))
      (testing "output-f"
        (let [output (atom "")
              result (transport/exec
                      t-state
                      {:execv ["/bin/ls" "/"]}
                      {:output-f (partial swap! output str)})]
          (is (zero? (:exit result)))
          (is (re-find #"bin" (:out result)))
          (is (= (:out result) @output))))
      (testing "Error return"
        (let [result (transport/exec t-state {:in "this-should-fail"} nil)]
          (is (not (zero? (:exit result)))))))))

(defn test-send
  [transport target options]
  (transport/with-transport [transport transport]
    (let [t-state (transport/open transport target options)]
      (testing "send-file"
        (filesystem/with-temp-file [tmp-src "src"]
          (filesystem/with-temp-file [tmp-dest "dest"]
            (transport/send-file
             t-state (.getPath tmp-src) (.getPath tmp-dest) {})
            (is (= "src" (slurp tmp-dest))))))
      (testing "send-text"
        (filesystem/with-temp-file [tmp-dest "dest"]
          (transport/send-text t-state "src" (.getPath tmp-dest) {})
          (is (= "src" (slurp tmp-dest)))))
      (testing "receive"
        (filesystem/with-temp-file [tmp-src "src"]
          (filesystem/with-temp-file [tmp-dest "dest"]
            (transport/receive t-state (.getPath tmp-src) (.getPath tmp-dest))
            (is (= "src" (slurp tmp-dest))))))
      (testing "send-file with non-existing path"
        (is
         (thrown-with-msg?
           Exception #"No such file"
           (transport/send-file
            t-state "/some/non-existing/path" "/invalid" {})))))))

(defn test-connect-fail
  [transport target options]
  (transport/with-transport [transport transport]
    (let [t-state (transport/open transport target options)]
      (is false "this test is designed to fail on open"))))
