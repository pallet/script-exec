(ns pallet.transport.ssh-test
  (:require
   [pallet.common.logging.logutils :as logutils]
   [pallet.transport.transport-test :as transport-test]
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
  (transport-test/test-exec
   (ssh/make-ssh-transport {})
   {:server "localhost"}
   {:user {:private-key-path (default-private-key-path)
           :public-key-path (default-public-key-path)
           :username (test-username)}}
   nil))

(deftest send-test
  (transport-test/test-send
   (ssh/make-ssh-transport {})
   {:server "localhost"}
   {:user {:private-key-path (default-private-key-path)
           :public-key-path (default-public-key-path)
           :username (test-username)}}
   nil))

(deftest connection-fail-test
  (is
   (thrown-with-msg?
     clojure.lang.ExceptionInfo #"SSH connect : server somewhere-non-existent"
     (transport-test/test-connect-fail
      (ssh/make-ssh-transport {})
      {:server "somewhere-non-existent"}
      {:user {:private-key-path (default-private-key-path)
              :public-key-path (default-public-key-path)
              :username (test-username)}}
      nil))))
