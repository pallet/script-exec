(ns pallet.transport.ssh
  "Pallet's ssh transport"
  (:refer-clojure :exclude [send])
  (:require
   [clj-ssh.ssh :as ssh]
   [clojure.contrib.condition :as condition]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.logging :as logging]
   [pallet.cache :as cache]
   [pallet.transport :as transport]))

(defonce default-agent-atom (atom nil))
(defn default-agent
  []
  (or @default-agent-atom
      (swap! default-agent-atom
             (fn [agent]
               (if agent
                 agent
                 (ssh/create-ssh-agent false))))))

(defn possibly-add-identity
  [agent private-key-path passphrase]
  (if passphrase
    (ssh/add-identity agent private-key-path passphrase)
    (ssh/add-identity-with-keychain agent private-key-path)))

(defn ssh-user-credentials
  "Middleware to user the session :user credentials for SSH authentication."
  [authentication]
  (let [user (:user authentication)]
    (logging/infof
     "SSH user %s %s" (:username user) (:private-key-path user))
    (possibly-add-identity
     (default-agent) (:private-key-path user) (:passphrase user))))

(defn connect-ssh-session
  [ssh-session endpoint authentication]
  (when-not (ssh/connected? ssh-session)
    (try
      (ssh/connect ssh-session)
      (catch Exception e
        (condition/raise
         :type :pallet/ssh-connection-failure
         :message (format
                   "ssh-fail: server %s, port %s, user %s"
                   (:server endpoint)
                   (:port endpoint 22)
                   (-> authentication :user :username))
         :cause e)))))

(defn connect-sftp-channel
  [sftp-channel endpoint authentication]
  (when-not (ssh/connected? sftp-channel)
      (try
        (ssh/connect sftp-channel)
        (catch Exception e
          (condition/raise
           :type :pallet/sftp-channel-failure
           :message (format
                     "ssh-fail: server %s, port %s, user %s"
                     (:server endpoint)
                     (:port endpoint 22)
                     (-> authentication :user :username))
           :cause e)))))

(defn connect
  ([endpoint authentication options]
     (let [ssh-session (ssh/session
                        (default-agent)
                        (:server endpoint)
                        :username (-> authentication :user :username)
                        :strict-host-key-checking (:strict-host-key-checking
                                                   options :no)
                        :port (:port endpoint 22)
                        :password (-> authentication :user :password))
           _ (.setDaemonThread ssh-session true)
           _ (connect-ssh-session ssh-session endpoint authentication)
           sftp-channel (ssh/ssh-sftp ssh-session)]
       (connect-sftp-channel sftp-channel endpoint authentication)
       {:ssh-session ssh-session
        :sftp-channel sftp-channel
        :endpoint endpoint
        :authentication authentication
        :options options}))
  ([state]
     (let [ssh-session (:ssh-session state)
           _ (connect-ssh-session ssh-session)
           sftp-channel (:sftp-channel state)]
       (connect-sftp-channel sftp-channel)
       state)))

(defn close
  "Close any ssh connection to the server specified in the session."
  [{:keys [ssh-session sftp-channel] :as state}]
  (when sftp-channel
    (ssh/disconnect sftp-channel))
  (when ssh-session
    (ssh/disconnect ssh-session))
  state)

(defn send
  [{:keys [sftp-channel] :as state} source destination]
  (ssh/sftp
   sftp-channel
   :put (io/input-stream (io/file source))
   destination
   :return-map true))

(defn send-str
  [{:keys [sftp-channel] :as state} source destination]
  (ssh/sftp
   sftp-channel
   :put (java.io.ByteArrayInputStream. (.getBytes source))
   destination
   :return-map true))

(defn receive
  [{:keys [sftp-channel] :as state} source destination]
  (ssh/sftp sftp-channel :get source (io/output-stream (io/file destination))))

(def
  ^{:doc "Specifies the buffer size used to read the ssh output stream.
    Defaults to 10K, to match clj-ssh.ssh/*piped-stream-buffer-size*"}
  ssh-output-buffer-size (atom (* 1024 10)))

(def
  ^{:doc "Specifies the polling period for retrieving ssh command output.
    Defaults to 1000ms."}
  output-poll-period (atom 1000))

(defn exec
  [{:keys [ssh-session sftp-channel endpoint authentication] :as state}
   {:keys [execv in] :as code}
   {:keys [output-f pty] :as options}]
  (logging/tracef "ssh/exec %s" code)
  (logging/tracef "ssh/exec %s" (pr-str state))
  (logging/tracef "ssh/exec session connected %s" (ssh/connected? ssh-session))
  (if output-f
    (let [[shell stream] (apply
                          ssh/ssh
                          ssh-session
                          (concat
                           (when-let [execv (seq execv)]
                             [(apply
                              str (interpose " " (map str execv)))])
                           [:in in :return-map true
                            :pty (:pty options true) :out :stream]))
          sb (StringBuilder.)
          buffer-size @ssh-output-buffer-size
          period @output-poll-period
          bytes (byte-array buffer-size)
          read-ouput (fn []
                       (when (pos? (.available stream))
                         (let [num-read (.read stream bytes 0 buffer-size)
                               s (String. bytes 0 num-read "UTF-8")]
                           (output-f s)
                           (.append sb s)
                           s)))]
      (while (ssh/connected? shell)
        (Thread/sleep period)
        (read-ouput))
      (while (read-ouput))
      (.close stream)
      (let [exit (.getExitStatus shell)
            stdout (str sb)]
        (if (zero? exit)
          {:out stdout :exit exit}
          (do
            (logging/errorf "Exit status  : %s" exit)
            {:out stdout :exit exit
             :error {:message (format
                               "Error executing script :\n :cmd %s\n :out %s\n"
                               code stdout)
                     :type :pallet-script-excution-error
                     :script-exit exit
                     :script-out stdout
                     :server (:server endpoint)}}))))
    (let [{:keys [out exit] :as result} (apply
                                         ssh/ssh
                                         ssh-session
                                         (concat
                                          (when-let [execv (seq execv)]
                                            [(apply
                                              str
                                              (interpose " " (map str execv)))])
                                          [:in in :return-map true
                                           :pty (:pty options true)]))]

      (if (zero? exit)
        result
        (do
          (logging/errorf "Exit status  : %s" exit)
          {:out out :exit exit
           :error {:message (format
                             "Error executing script :\n :cmd %s\n :out %s\n"
                             code out)
                   :type :pallet-script-excution-error
                   :script-exit exit
                   :script-out out
                   :server (:server endpoint)}})))))

(defn forward-to-local
  [{:keys [ssh-session sftp-channel endpoint authentication] :as state}
   remote-port
   local-port]
  (.setPortForwardingL ssh-session local-port (:server endpoint) remote-port))

(defn unforward-to-local
  [{:keys [ssh-session sftp-channel endpoint authentication] :as state}
   remote-port
   local-port]
  (.delPortForwardingL ssh-session local-port))

(deftype SshTransportState
    [state]
  transport/TransportState
  (open? [_]
    (ssh/connected? (:ssh-session state)))
  (re-open [_]
    (connect state))
  (close [_]
    (close state))
  transport/Transfer
  (send [_ source destination]
    (send state source destination))
  (send-str [_ source destination]
    (send-str state source destination))
  (receive [_ source destination]
    (receive state source destination))
  transport/Exec
  (exec [transport-state code options]
    (exec state code options))
  transport/PortForward
  (forward-to-local [transport-state remote-port local-port]
    (forward-to-local transport-state remote-port local-port))
  (unforward-to-local [transport-state remote-port local-port]
    (unforward-to-local transport-state remote-port local-port)))

(deftype SshTransport [connection-cache]
  transport/Transport
  (connection-based? [_]
    true)
  (open [_ endpoint authentication options]
    (ssh-user-credentials authentication)
    (or
     (get connection-cache [endpoint authentication options])
     (let [state (SshTransportState.
                  (connect endpoint authentication options))]
       (cache/miss connection-cache [endpoint authentication options] state)
       state)))
  (close-transport [_]
    (cache/expire-all connection-cache)))

(defn make-ssh-transport
  [{:keys [limit] :or {limit 20}}]
  (SshTransport.
   (cache/make-fifo-cache :limit limit :expire-f transport/close)))
