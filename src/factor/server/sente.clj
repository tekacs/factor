(ns factor.server.sente
  "A server for sente, with event handling set up"
  (:require [clojure.core.async :refer [close!]]
            [factor.async :as async]
            [factor.encoding :as encoding]
            [factor.server.injection :as injection]
            [factor.server.multimethods :as multimethods]
            [methodical.core :as methodical]
            [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :refer [->TransitPacker]]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [taoensso.timbre :as timbre])
  (:import (java.util UUID)))

(def packer
  "A transit+json-based packer to send/receive from the sente client using encoding readers/writers"
  (->TransitPacker :json {:handlers encoding/write-handlers} {:handlers encoding/read-handlers}))

;; This can be extended by providing a `dispatch-map`, by adding an `:after ::handle-event!` method with methodical, or both.
(methodical/defmethod injection/init-key ::handle-event! [_ {:keys [dispatch-map]} _]
  ;; A handler that receives all server-side events from sente:
  ;; - sente system events  (all of the :chsk/* events)
  ;; - client-sent events (anything else, passed through as-is from the client)
  (let [multifn
        (-> (multimethods/clojure-multimethod (fn [_ {[event-kwd _] :event}] event-kwd))
            (methodical/add-primary-method :default (fn [_ msg] (doto [:unhandled-event (-> msg :event first) (keys msg)] timbre/info)))
            (methodical/add-primary-method :chsk/uidport-close (fn [_ {:keys [client-id uid]}] (doto [:client-disconnected {:client-id client-id :uid uid}] timbre/info)))
            (methodical/add-primary-method :chsk/uidport-open (fn [_ {:keys [client-id uid]}] (doto [:client-connected {:client-id client-id :uid uid}] timbre/info)))
            (methodical/add-primary-method :chsk/bad-package (fn [_ msg] (doto [:invalid-serialization msg] timbre/info)))
            (methodical/add-primary-method :chsk/ws-ping (fn [_ _] "This is just a keepalive message")))]
    (cond-> multifn
      dispatch-map (multimethods/add-methods dispatch-map))))

(methodical/defmethod injection/prep-key ::server-options [_ config]
  (update config :user-id-fn #(or % :session-id)))

;; The default user-id is extracted from Ring parameters, as sent by client.sente.
;; To extract the user-id in a different way, just redefine this defmethod downstream.
(methodical/defmethod injection/init-key ::server-options
  [_ {:keys [user-id-fn]}]
  {:csrf-token-fn nil
   :packer        packer
   :user-id-fn    (fn [req] (some-> req :params user-id-fn (UUID/fromString)))})

;; The actual sente server.
(methodical/defmethod injection/init-key ::server [_ {:keys [options]}]
  (let [{:keys [ch-recv send-fn connected-uids ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket-server! (get-sch-adapter) options)]
    {:post-handler ajax-post-fn
     :get-and-ws-handler ajax-get-or-ws-handshake-fn
     :receive-channel ch-recv
     :send! send-fn
     :connected-uids$ connected-uids}))

(methodical/defmethod injection/halt-key! ::server [_ {:keys [receive-channel]}]
  (close! receive-channel))

;; A reaction loop, which calls `handle-event!` for every server-side sente message.
(methodical/defmethod injection/init-key ::handler [_ {:keys [handle-event! server context]}]
  (async/reaction-loop
   (:receive-channel server)
   (fn [msg] (handle-event! context msg))))

(methodical/defmethod injection/halt-key! ::handler [_ channel]
  (close! channel))

(def config
  {::handle-event!
   {}
   
   ::server-options
   {:user-id-fn nil}

   ::server
   {:options (injection/ref :factor.server.sente/server-options)}

   ::handler
   {:server  (injection/ref :factor.server.sente/server)
    :context (injection/ref :factor/context)}})
