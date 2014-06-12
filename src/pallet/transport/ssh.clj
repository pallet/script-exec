(ns pallet.transport.ssh
  "Pallet's ssh transport"
  (:refer-clojure :exclude [send])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.logging :as logging]
   [com.palletops.cache-resources :as cache]
   [pallet.common.context :as context]
   [pallet.transport :as transport]
   [pallet.transport.protocols :as impl]
   [pallet.ssh.transport :as ssh-transport]))

(deftype SshTransportState
    [state]
  impl/TransportState
  (open? [_]
    (ssh-transport/connected? state))
  (re-open [_]
    (ssh-transport/connect state))
  (close [_]
    (ssh-transport/close state))
  impl/Transfer
  (send-stream [_ source destination options]
    (ssh-transport/send-stream state source destination options))
  (receive [_ source destination]
    (ssh-transport/receive state source destination))
  impl/Exec
  (exec [transport-state code options]
    (ssh-transport/exec state code options))
  impl/PortForward
  (forward-to-local [transport-state local-port remote-port remote-host]
    (ssh-transport/forward-to-local state local-port remote-port remote-host))
  (unforward-to-local [transport-state local-port]
    (ssh-transport/unforward-to-local state local-port)))

(defn lookup-or-create-state
  [cache target options]
  (or
   (when-let [state (cache/atomic-release cache [target options])]
     ;; Remove the state from the cache, so it doesn't get expired.
     ;; Note that there is a race here.
     (if (impl/open? state)
       state
       (logging/debugf
        "lookup-or-create-state cache hit on closed session %s"
        (mapv
         #(update-in % [:credentials] ssh-transport/obfuscate-credentials)
         target))))
   (do
     (logging/debugf
      "Create ssh transport state: %s"
      (mapv
       #(update-in % [:credentials] ssh-transport/obfuscate-credentials)
       target))
     (SshTransportState. (ssh-transport/open target options)))))

(defn open [cache target options]
  (lookup-or-create-state cache target options))

(defn release [cache state]
  (let [{:keys [target options]} (.state state)]
    (logging/debugf
     "Caching ssh transport state: %s"
     (mapv
      #(update-in % [:credentials] ssh-transport/obfuscate-credentials)
      target))
    (swap! cache assoc [target options] state)))

(deftype SshTransport [connection-cache]
  impl/Transport
  (connection-based? [_]
    true)
  (open [_ target options]
    (open connection-cache target options))
  (release [_ state]
    (release connection-cache state))
  (close-transport [_]
    (logging/debug "SSH close-transport")
    (empty connection-cache)))

(defn make-ssh-transport
  [{:keys [limit] :or {limit 20}}]
  (SshTransport.
   (atom
    (cache/fifo-cache-factory {} {:threshold limit :expire-f impl/close}))))

(defmethod transport/factory :ssh
  [_ options]
  (make-ssh-transport options))
