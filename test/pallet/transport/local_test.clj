(ns pallet.transport.local-test
  (:require
   [pallet.transport :as transport]
   [pallet.transport.local :as local]
   [pallet.utils :as utils])
  (:use
   clojure.test))

(deftest exec-test
  (let [transport (local/make-local-transport)
          t-state (transport/open transport nil nil nil)]
    (testing "Default shell"
      (let [result (transport/exec t-state {:in "ls /"} nil)]
        (is (zero? (:exit result)))
        (is (re-find #"bin" (:out result)))))
    (testing "Explicit shell"
      (let [result (transport/exec
                    t-state {:execv ["/bin/bash"] :in "ls /"} nil)]
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
                    {:execv ["/bin/ls" "/"]}
                    {:output-f (partial swap! output str)})]
        (is (zero? (:exit result)))
        (is (re-find #"bin" (:out result)))
        (is (= (:out result) @output))))
    (testing "Error return"
      (let [result (transport/exec t-state {:in "this-should-fail"} nil)]
        (is (not (zero? (:exit result))))))))

(deftest send-test
  (let [transport (local/make-local-transport)
        t-state (transport/open transport nil nil nil)]
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
