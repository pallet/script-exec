(ns pallet.transport.data-driver
  "Allow data driven use of pallet.transport"
  (:require
   [pallet.transport :as transport]
   [pallet.transport.local :as local] ;; ensure transports are loaded
   [pallet.transport.ssh :as ssh]))

(defn exec
  "Execute code based "
  [{:keys [transport options code endpoint authentication]}]
  (let [transport (transport/factory transport options)
        connection (transport/open transport endpoint authentication options)]
    (try
      (transport/exec connection code options)
      (finally
       (transport/close-transport transport)))))
