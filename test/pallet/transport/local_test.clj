(ns pallet.transport.local-test
  (:require
   [pallet.transport.transport-test :as transport-test]
   [pallet.transport.local :as local])
  (:use
   clojure.test))

(deftest exec-test
  (transport-test/test-exec (local/make-local-transport) nil nil)
  ;; (testing "Explicit shell"
  ;;   (let [result (transport/exec
  ;;                 t-state {:execv ["/bin/bash"] :in "ls /"} nil)]
  ;;     (is (zero? (:exit result)))
  ;;     (is (re-find #"bin" (:out result)))))
  )

(deftest send-test
  (transport-test/test-send (local/make-local-transport) nil nil))
