(ns pallet.transport
  "A transport provides file transfer and execution facilities"
  (:refer-clojure :exclude [send])
  (:require
   [clojure.java.io :as io]
   [pallet.common.context :as context]))

(defprotocol Transport
  "A transport can be message or connection based.
State is protocol dependent.
Authentication is protocol dependent.
Endpoint is protocol dependent.
Options are protocol dependent.
Should be closeable to allow cleanup.
Connections can be expensive, so need to be cached and be poolable."
  (connection-based? [transport]
    "Predicate for a connection based protocol")
  (open [transport state] [transport endpoint authentication options]
    "Returns a state object for the given endpoint and authentication maps.
     The returned state should (initially) satisfy open?")
  (close-transport [_]
    "Release any resources held by the transport. Does not close any
     transport state objects."))

(defprotocol TransportState
  "Represents the state of a transport's communication with an endpoint
   and given authentication."
  (open? [transport-state]
    "Predicate for testing if the given transport state is open. For a
     connection based protocol, this would mean it was connected. For a
     message based protocol, that the endpoint is reachable.")
  (re-open [transport-state]
    "Re-opens the transport-state. The state will (initially) satisfy open?")
  (close [transport-state]
    "Release any resources associated with state."))

(defprotocol TransportEndpoint
  "Represents the endpoint referred to by a transport's state."
  (endpoint [transport-state]
    "The endpoint being communitcated with.")
  (authentication [transport-state]
    "Authenticatino being used"))

(defprotocol Transfer
  "Transfer data over a transport."
  (send [transport-state input-stream destination]
    "Send data from source input-stream to destination file, using the transport
     state.")
  (receive [transport-state source destination]
    "Receive data from source file path  and store in destination file path
     using the transport state."))

(defn send-text
  [transport-state text destination]
  (context/with-context
    (format "Send text %s to %s" text destination) {}
    (send
     transport-state
     (java.io.ByteArrayInputStream. (.getBytes text))
     destination)))

(defn send-file
  [transport-state filepath destination]
  (context/with-context
    (format "Send file %s to %s" filepath destination) {}
    (send transport-state (io/input-stream (io/file filepath)) destination)))

(defprotocol Exec
  "Execute code over the transport."
  (exec [transport-state code options]
    "The code argument should be a map, with an :execv key specifying the
     command and arguments to be run. If the :in key is specified, it should
     be a string to attach to the process' stdin. If :in is specified
     and :execv is nil, then execution should be within a shell.

     The `options` map recognises the following keys:
       :output-f a function to be notified with incremental output.

     It returns a map with :exit and :out keys."))

(defprotocol PortForward
  "Forward a port over a transport."
  (forward-to-local [transport-state remote-port local-port]
    "Map the target's remote-port to the given local-port")
  (unforward-to-local [transport-state remote-port local-port]
    "Unmap the target's remote-port to the given local-port"))


(defmacro with-ssh-tunnel
  "Execute the body with an ssh-tunnel available for the ports given in the
   tunnels map. Automatically closes port forwards on completion.

   Tunnels should be a map from local ports (integers) to either
     1) An integer remote port. Remote host is assumed to be \"localhost\".
     2) A vector of remote host and remote port. eg, [\"yahoo.com\" 80].

   e.g.
        (with-ssh-tunnel session {2222 22}
           ;; do something on local port 2222
           session)"
  [transport-state tunnels & body]
  `(let [transport-state# ~transport-state
         tunnels# ~tunnels
         unforward# (fn []
                      (doseq [[lport# rport#] tunnels#]
                        (try
                          (transport/unforward-to-local
                           transport-state# rport# lport#)
                          (catch Exception e#
                            (logging/warnf
                             "Removing Port forward to %s failed: %s"
                             lport# (.getMessage e#))))))]
     (try
       ;; Set up the port forwards
       (doseq [[lport# rspec#] tunnels#
               :let [[rhost# rport#] (if (sequential? rspec#)
                                       rspec#
                                       ["localhost" rspec#])]]
         (transport/forward-to-local transport-state# rport# lport#))
       ~@body
       (finally (unforward#)))))

(defmacro with-transport
  "Define a scope where the given name is bound to a transport.
   The close-transport function is called on the transport on exit from the
   scope."
  [[name transport] & body]
  `(let [~name ~transport]
     (try
       ~@body
       (finally (close-transport ~name)))))

(defmulti factory (fn [transport-kw options] transport-kw))
