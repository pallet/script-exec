(ns pallet.transport.ssh
  "Pallet's ssh transport"
  (:refer-clojure :exclude [send])
  (:require
   [clj-ssh.ssh :as ssh]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.logging :as logging]
   [pallet.cache :as cache]
   [pallet.common.context :as context]
   [pallet.transport :as transport]
   [pallet.ssh.transport :as ssh-transport]))


(deftype SshTransportState
    [state]
  transport/TransportState
  (open? [_]
    (ssh-transport/connected? (:ssh-session state)))
  (re-open [_]
    (ssh-transport/connect state))
  (close [_]
    (ssh-transport/close state))
  transport/TransportEndpoint
  (endpoint [_]
    (:endpoint state))
  (authentication [_]
    (:authentication state))
  transport/Transfer
  (send-stream [_ source destination options]
    (ssh-transport/send-stream state source destination options))
  (receive [_ source destination]
    (ssh-transport/receive state source destination))
  transport/Exec
  (exec [transport-state code options]
    (ssh-transport/exec state code options))
  transport/PortForward
  (forward-to-local [transport-state remote-port local-port]
    (ssh-transport/forward-to-local transport-state remote-port local-port))
  (unforward-to-local [transport-state remote-port local-port]
    (ssh-transport/unforward-to-local transport-state remote-port local-port)))

(defn lookup-or-create-state
  [cache endpoint authentication options]
  (or
   (when-let [state (get cache [endpoint authentication options])]
     (when (transport/open? state)
       state
       (do (connect state) state)))
   (let [state (SshTransportState. (connect endpoint authentication options))]
     (logging/debugf "Create ssh transport state: %s" endpoint)
     (cache/miss cache [endpoint authentication options] state)
     state)))

(defn open [cache endpoint authentication options]
  (ssh-transport/ssh-user-credentials authentication)
  (lookup-or-create-state cache endpoint authentication options))

(deftype SshTransport [connection-cache]
  transport/Transport
  (connection-based? [_]
    true)
  (open [_ endpoint authentication options]
    (open connection-cache endpoint authentication options))
  (close-transport [_]
    (logging/debug "SSH close-transport")
    (cache/expire-all connection-cache)))

(defn make-ssh-transport
  [{:keys [limit] :or {limit 20}}]
  (SshTransport.
   (cache/make-fifo-cache :limit limit :expire-f transport/close)))

(defmethod transport/factory :ssh
  [_ options]
  (make-ssh-transport options))
