(ns factor.server.sente
  "A server for sente, with event handling set up"
  (:require [clojure.core.async :refer [close!]]
            [factor.async :as async]
            [factor.encoding :as encoding]
            [integrant.core :as ig]
            [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :refer [->TransitPacker]]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [taoensso.timbre :as timbre])
  (:import (java.util UUID)))

(def packer
  "A transit+json-based packer to send/receive from the sente client using encoding readers/writers"
  (->TransitPacker :json {:handlers encoding/write-handlers} {:handlers encoding/read-handlers}))

(defmulti handle-event!
  "A handler that receives all server-side events from sente.
  
  These are:
  - sente system events  (all of the :chsk/* events)
  - client-sent events (anything else, passed through as-is from the client)
  "
  (fn [_ {[event-kwd _] :event}] event-kwd))

(defmethod handle-event! :default
  default [_ msg]
  (timbre/info [:unhandled-event (-> msg :event first) (keys msg)]))

(defmethod handle-event! :chsk/uidport-close
  client-disconnected [_ {:keys [client-id uid]}]
  (timbre/info [:client-disconnected {:client-id client-id :uid uid}]))

(defmethod handle-event! :chsk/uidport-open
  client-connected [_ {:keys [client-id uid]}]
  (timbre/info [:client-connected {:client-id client-id :uid uid}]))

(defmethod handle-event! :chsk/bad-package
  invalid-serialization [_ msg]
  (timbre/info [:invalid-serialization msg]))

(defmethod handle-event! :chsk/ws-ping
  ignored-keepalive [_ _]
  "This is just a keepalive message")

(defmethod ig/prep-key ::server-options [_ config]
  (update config :user-id-fn #(or % :session-id)))

;; The default user-id is extracted from Ring parameters, as sent by client.sente.
;; To extract the user-id in a different way, just redefine this defmethod downstream.
(defmethod ig/init-key ::server-options
  server-options [_ {:keys [user-id-fn]}]
  {:csrf-token-fn nil
   :packer        packer
   :user-id-fn    (fn [req] (some-> req :params user-id-fn (UUID/fromString)))})

;; The actual sente server.
(defmethod ig/init-key ::server [_ {:keys [options]}]
  (let [{:keys [ch-recv send-fn connected-uids ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket-server! (get-sch-adapter) options)]
    {:post-handler ajax-post-fn
     :get-and-ws-handler ajax-get-or-ws-handshake-fn
     :receive-channel ch-recv
     :send! send-fn
     :connected-uids$ connected-uids}))

(defmethod ig/halt-key! ::server [_ {:keys [receive-channel]}]
  (close! receive-channel))

;; A reaction loop, which calls `handle-event!` for every server-side sente message.
(defmethod ig/init-key ::handler [_ {:keys [server context]}]
  (async/reaction-loop
   (:receive-channel server)
   (fn [msg] (handle-event! context msg))))

(defmethod ig/halt-key! ::handler [_ channel]
  (close! channel))
