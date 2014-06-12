(ns pallet.transport.protocols
  "Transport protocols.")

(defprotocol Transport
  "A transport can be message or connection based.
A transport can be message or connection based.  State, Target, and
Options are transport dependent.  Should be closeable to allow
cleanup.  Connections can be expensive, so need to be cached and be
poolable."
  (connection-based? [transport]
    "Predicate for a connection based protocol")
  (open [transport target options]
    "Returns a state object for the given endpoint and authentication maps.
     The returned state should (initially) satisfy open?")
  (release [transport state]
    "Release any resources for the specified state.")
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

(defprotocol Transfer
  "Transfer data over a transport."
  (send-stream [transport-state input-stream destination {:keys [mode]}]
    "Send data from source input-stream or to destination file, using the
     transport state. Optionally set the mode of the destination file.")
  (receive [transport-state source destination]
    "Receive data from source file path  and store in destination file path
     using the transport state."))

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
  (forward-to-local [transport-state local-port remote-port remote-host]
    "Map the target's remote-port to the given local-port")
  (unforward-to-local [transport-state local-port]
    "Unmap the target's remote-port to the given local-port"))
