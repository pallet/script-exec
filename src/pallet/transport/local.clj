(ns pallet.transport.local
  "Local transport"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.logging :as logging]
   [pallet.shell :as shell]
   [pallet.transport :as transport]))

(def
  ^{:doc "Specifies the buffer size used to read the output stream.
    Defaults to 10K"}
  output-buffer-size (atom (* 1024 10)))

(def
  ^{:doc "Specifies the polling period for retrieving command output.
    Defaults to 1000ms."}
  output-poll-period (atom 1000))

(defn read-buffer [stream output-f]
  (let [buffer-size @output-buffer-size
        bytes (byte-array buffer-size)
        sb (StringBuilder.)]
    {:sb sb
     :reader (fn []
               (when (pos? (.available stream))
                 (let [num-read (.read stream bytes 0 buffer-size)
                       s (String. bytes 0 num-read "UTF-8")]
                   (output-f s)
                   (.append sb s)
                   s)))}))

(defn sh-script
  "Run a script on local machine.

   Command:
     :execv  sequence of command and arguments to be run (default /bin/bash).
     :in     standard input for the process.
   Options:
     :output-f  function to incrementally process output"
  [{:keys [execv in] :as command}
   {:keys [filter-f output-f] :or {filter-f identity} :as options}]
  (logging/tracef "sh-script %s" command)
  (if output-f
    (try
      (let [{:keys [out err proc]} (apply
                                    shell/sh
                                    (concat
                                     (or execv ["/bin/bash"]) ;; TODO generalise
                                     [:in in :async true]))
            out-reader (read-buffer out output-f)
            err-reader (read-buffer err output-f)
            period @output-poll-period
            read-out (:reader out-reader)
            read-err (:reader err-reader)]
        (with-open [out out err err]
          (while (not (try (.exitValue proc)
                           (catch IllegalThreadStateException _)))
            (Thread/sleep period)
            (read-out)
            (read-err))
          (while (read-out))
          (while (read-err))
          (let [exit (.exitValue proc)]
            {:exit exit
             :out (str (:sb out-reader))
             :err (str (:sb err-reader))}))))
    (apply shell/sh (concat (or execv ["/bin/bash"]) [:in in]))))

(deftype LocalTransportState []
  transport/TransportState
  (open? [_] true)
  (re-open [_])
  (close [_])
  transport/Transfer
  (send [_ source destination]
    (io/copy (io/file source) (io/file destination)))
  (send-str [_ source destination]
    (io/copy source (io/file destination)))
  (receive [_ source destination]
    (io/copy (io/file source) (io/file destination)))
  transport/Exec
  (exec [transport-state code options]
    (sh-script code options)))

(deftype LocalTransport []
  transport/Transport
  (connection-based? [_] false)
  (open [_ endpoint authentication options] (LocalTransportState.))
  (close-transport [_]))

(defn make-local-transport
  []
  (LocalTransport.))
