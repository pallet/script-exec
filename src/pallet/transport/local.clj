(ns pallet.transport.local
  "Local transport"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.logging :as logging]
   [pallet.common.context :as context]
   [pallet.shell :as shell]
   [pallet.transport :as transport]
   [pallet.transport.protocols :as impl]
   [pallet.local.transport :as local]))

(deftype LocalTransportState []
  impl/TransportState
  (open? [_] true)
  (re-open [_])
  (close [_])
  impl/Transfer
  (send-stream [_ source destination options]
    (local/send-stream source destination options))
  (receive [_ source destination]
    (local/send-stream (io/input-stream source) destination {}))
  impl/Exec
  (exec [transport-state code options]
    (local/exec code options)))

(deftype LocalTransport []
  impl/Transport
  (connection-based? [_] false)
  (open [_ target options] (LocalTransportState.))
  (release [_ state])
  (close-transport [_]))

(defn make-local-transport
  []
  (LocalTransport.))

(defmethod transport/factory :local
  [_ options]
  (make-local-transport))
