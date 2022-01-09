(ns factor.sente.server
  "A server for sente, with event handling set up"
  (:require [clojure.core.async :refer [close!]]
            [factor.async :as async]
            [factor.identifiers :as identifiers]
            [factor.multimethods :as multimethods]
            [factor.sente :as sente]
            [integrant.core :as ig]
            [taoensso.sente :as sente-core]
            [taoensso.timbre :as timbre]))

(defmethod ig/init-key ::handle-event! [_ {:keys [dispatch-map]}]
  (multimethods/map-multimethod
   (fn [_ {[event-kwd _] :event}] event-kwd)
   (merge
    {:default (fn [_ msg] (doto [:unhandled-event (-> msg :event first) (keys msg)] timbre/info))
     ;; server-side events: https://github.com/ptaoussanis/sente/blob/7a1fad84cad9839834648a3d89dce8c1807d2f70/src/taoensso/sente.cljc#L37
     :chsk/uidport-close (fn [_ {:keys [client-id uid]}] (doto [:client-disconnected {:client-id client-id :uid uid}] timbre/info))
     :chsk/uidport-open (fn [_ {:keys [client-id uid]}] (doto [:client-connected {:client-id client-id :uid uid}] timbre/info))
     :chsk/bad-package (fn [_ msg] (doto [:invalid-serialization msg] timbre/info))
     :chsk/ws-ping (fn [_ _] "This is just a keepalive message")}
     ;; We also receive events from the client as-sent -- those should be handled by dispatch-map.
    dispatch-map)))

(defmethod ig/prep-key ::server-options [_ config]
  (update config :user-id-fn #(or % :session-id)))

;; The default user-id is extracted from Ring parameters, as sent by client.sente.
;; To extract the user-id in a different way, just redefine this defmethod downstream.
(defmethod ig/init-key ::server-options [_ {:keys [packer user-id-fn config]}]
  (merge
   {:csrf-token-fn nil
    :packer        packer
    :user-id-fn    (fn [req] (some-> req :params user-id-fn identifiers/read-uuid))}
   config))

;; The actual sente server.
(defmethod ig/init-key ::server [_ {:keys [http-server-adapter options]}]
  (let [{:keys [ch-recv send-fn connected-uids ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente-core/make-channel-socket-server! http-server-adapter options)]
    {:post-handler ajax-post-fn
     :get-and-ws-handler ajax-get-or-ws-handshake-fn
     :receive-channel ch-recv
     :send! send-fn
     :connected-uids$ connected-uids}))

(defmethod ig/halt-key! ::server [_ {:keys [receive-channel]}]
  (close! receive-channel))

;; A reaction loop, which calls `handle-event!` for every server-side sente message.
(defmethod ig/init-key ::handler [_ {:keys [handle-event! server context]}]
  (async/reaction-loop
   (:receive-channel server)
   (fn [msg] (handle-event! context msg))))

(defmethod ig/halt-key! ::handler [_ channel]
  (close! channel))

(def config
  {::handle-event!
   {}

   ::server-options
   {:packer (ig/ref ::sente/packer) :user-id-fn nil}

   ::server
   {:http-server-adapter (ig/ref :factor.server.sente-http/http-server-adapter)
    :options (ig/ref ::server-options)}

   ::handler
   {:handle-event! (ig/ref ::handle-event!)
    :server  (ig/ref ::server)
    :context (ig/ref :factor/context)}})
