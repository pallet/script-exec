(ns pallet.transport
  "A transport provides file transfer and execution facilities"
  (:refer-clojure :exclude [send])
  (:require
   [clojure.java.io :as io]
   [pallet.transport.protocols :as impl]))

;;; # Transport
;;; A transport can be message or connection based.  State,
;;; Target,  and Options are transport dependent.
;;; Should be closeable to allow cleanup.  Connections may be
;;; expensive, so may be cached and poolable.

(defn connection-based?
 "Predicate for a connection based protocol"
 [transport]
 (impl/connection-based? transport))

(defn open
  "Returns a state object for the given target. The returned state
  should (initially) satisfy open?."
  [transport target options]
  (impl/open transport target options))

(defn release
  "Release any resources for the specified state.
  This may still cache the resources."
  [transport state]
  (impl/release transport state))

(defn close-transport
  "Release any resources held by the transport. Does not close any
  transport state objects."
  [transport]
  (impl/close-transport transport))

;;; # Transport State

;;; Represents the state of a transport's communication with an
;;; endpoint and given authentication.
(defn open?
  "Predicate for testing if the given transport state is open. For a
  connection based protocol, this would mean it was connected. For a
  message based protocol, that the endpoint is reachable."
  [transport-state]
  (impl/open? transport-state))

(defn re-open
  "Re-opens the transport-state. The state will (initially) satisfy open?"
  [transport-state]
  (impl/re-open transport-state))

(defn close
  "Release any resources associated with transport-state."
  [transport-state]
  (impl/close transport-state))

;;; # Transfers

;;; Transfer data over a transport.
(defn send-stream
  "Send data from source input-stream or to destination file, using the
  transport state. Optionally set the mode of the destination file."
  [transport-state input-stream destination {:keys [mode] :as options}]
  (impl/send-stream transport-state input-stream destination options))

(defn send-text
  "Send a string literal to a remote file."
  [transport-state ^String text destination {:keys [mode] :as options}]
  (impl/send-stream
   transport-state
   (java.io.ByteArrayInputStream. (.getBytes text))
   destination
   options))

(defn send-file
  "Send a file to a remote file."
  [transport-state filepath destination {:keys [mode] :as options}]
  (impl/send-stream
   transport-state (io/input-stream (io/file filepath)) destination options))

(defn receive
  "Receive data from source file path  and store in destination file path
  using the transport state."
  [transport-state source destination]
  (impl/receive transport-state source destination))

;;; # Execute code

;;; Execute code over the transport.
(defn exec
  "The code argument should be a map, with an :execv key specifying the
  command and arguments to be run. If the :in key is specified, it should
  be a string to attach to the process' stdin. If :in is specified
  and :execv is nil, then execution should be within a shell.

  The `options` map recognises the following keys:
    :output-f a function to be notified with incremental output.

  It returns a map with :exit and :out keys."
  [transport-state code options]
  (impl/exec transport-state code options))

;;; # Port Forwarding
;;; Forward a port over a transport.
(defn forward-to-local
  "Map the target's remote-port to the given local-port"
  [transport-state remote-port local-port]
  (impl/forward-to-local transport-state remote-port local-port))

(defn unforward-to-local
  "Unmap the target's remote-port to the given local-port"
  [transport-state remote-port local-port]
  (impl/unforward-to-local transport-state remote-port local-port))


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

(defmulti factory
  "Factory for creating transport objects based on a keyword
  identifying the trasport."
  (fn [transport-kw options] transport-kw))
