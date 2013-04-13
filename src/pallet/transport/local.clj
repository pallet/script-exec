(ns pallet.transport.local
  "Local transport"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.logging :as logging]
   [pallet.common.context :as context]
   [pallet.shell :as shell]
   [pallet.transport :as transport]
   [pallet.local.transport :as local]))

(deftype LocalTransportState []
  transport/TransportState
  (open? [_] true)
  (re-open [_])
  (close [_])
  transport/Transfer
  (send-stream [_ source destination options]
    (local/send-stream source destination options))
  (receive [_ source destination]
    (local/send-stream (io/input-stream source) destination {}))
  transport/Exec
  (exec [transport-state code options]
    (local/exec code options)))

(deftype LocalTransport []
  transport/Transport
  (connection-based? [_] false)
  (open [_ endpoint authentication options] (LocalTransportState.))
  (release [_ endpoint authentication options])
  (close-transport [_]))

(defn make-local-transport
  []
  (LocalTransport.))

(defmethod transport/factory :local
  [_ options]
  (make-local-transport))
