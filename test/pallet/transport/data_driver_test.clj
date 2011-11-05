(ns pallet.transport.data-driver-test
  (:require
   [pallet.common.logging.logutils :as logutils]
   [pallet.transport.data-driver :as data-driver]
   [pallet.transport.ssh-test :as ssh-test])
  (:use
   clojure.test))

(use-fixtures :once (logutils/logging-threshold-fixture))

(deftest data-driver-test
  (let [{:keys [exit out]} (data-driver/exec
                            {:transport :local
                             :code {:in "ls /; exit 0"}})]
    (is (zero? exit))
    (is (re-find #"bin" out)))
  (let [{:keys [exit out]}
        (data-driver/exec
         {:transport :ssh
          :endpoint {:server "localhost"}
          :authentication
          {:user {:private-key-path (ssh-test/default-private-key-path)
                  :public-key-path (ssh-test/default-public-key-path)
                  :username (ssh-test/test-username)}}
          :code {:in "ls /; exit 0"}})]
    (is (zero? exit))
    (is (re-find #"bin" out))))
