(ns factor.sente.client
  (:require [clojure.core.async :as y]
            [factor.async :as async]
            [factor.multimethods :as multimethods]
            [factor.sente :as sente]
            [factor.system :as system]
            [factor.types :as ty]
            [integrant.core :as ig]
            [taoensso.sente :as sente-core]
            [taoensso.timbre :as timbre]))

(defmethod system/pre-init-spec ::client-options [_]
  [:map
   [:protocol [:enum :http :https]]
   [:host :string]
   [:port :int]
   [:path :string]
   [:params {:optional true} [:maybe :map]]
   [:extra-config {:optional true} [:maybe :map]]])

(defmethod ig/init-key ::client-options [_ {:keys [protocol host port path params extra-config]}]
  {:protocol     protocol
   :host         host
   :port         port
   :path         path
   :params       params
   :extra-config extra-config})

(defmethod ig/init-key ::client [_ {:keys [packer client-options]}]
  (let [{:keys [host port protocol path params extra-config]} client-options
        {:keys [chsk ch-recv send-fn state]}
        (sente-core/make-channel-socket-client!
         path
         "deliberately-left-blank"
         (merge
          {:type     :auto
           :packer   packer
           :host     host
           :port     port
           :protocol protocol
           :params   params}
          extra-config))]
    {:chsk            chsk
     :receive-channel ch-recv
     :send!           send-fn
     :state$          state}))

(defmethod ig/suspend-key! ::client [_ _])

(defmethod ig/resume-key ::client [_ _ _ old-client]
  (sente-core/chsk-reconnect! (:chsk old-client))
  old-client)

(defmethod ig/halt-key! ::client [_ client]
  (sente-core/chsk-disconnect! (:chsk client)))

(defmethod ig/init-key ::handle-event! [_ {:keys [dispatch-map]}]
  (multimethods/map-multimethod
   (fn [_ {[event-kwd _] :event}] event-kwd)
   (merge
    {:default (fn [_ msg] (doto [:unhandled-event (-> msg :event first) (keys msg)] timbre/info))
          ;; client-side events: https://github.com/ptaoussanis/sente/blob/7a1fad84cad9839834648a3d89dce8c1807d2f70/src/taoensso/sente.cljc#L31
     :chsk/recv (fn [_ _])
     :chsk/handshake (fn [_ _])
     :chsk/state (fn [_ _])
     :chsk/ws-ping (fn [_ _] "This is just a keepalive message")}
         ;; As of the time of writing, there are no additional events to handle client-side -- server-side events are received inside :chsk/recv
         ;; dispatch-map will primarily be used to override these default handlers.
    dispatch-map)))

(defmethod ig/init-key ::handler [_ {:keys [handle-event! client context]}]
  (async/reaction-loop (:receive-channel client) (partial handle-event! context)))

(defmethod ig/halt-key! ::handler [_ reaction-loop]
  (y/close! reaction-loop))

;; This sender wraps the raw sente client in disconnection-friendly logic.
;; The sender's input channel takes vectors of arguments, to be applied to the sente client's `send-fn` / `send!` function when ready.
;; See https://github.com/ptaoussanis/sente/blob/7a1fad84cad9839834648a3d89dce8c1807d2f70/src/taoensso/sente.cljc#L1584 for send-fn parameters
(defmethod ig/init-key ::sender [_ {:keys [client]}]
  (let [{:keys [send! state$]} client
        input                  (y/chan)
        pending                (volatile! [])]
    (y/go-loop []
      (let [[?next-message from] (y/alts! [input (if (seq @pending)
                                                   (y/timeout 100)
                                                   (y/promise-chan))]
                                          {:priority true})
            {:keys [open?]}      @state$
            input?               (= from input)]
        ;; If the socket is open, send any pending messages.
        (when (and open? (seq @pending))
          (doseq [message @pending]
            (apply send! message))
          (vreset! pending []))
        ;; There are redundant checks in these branch conditions to make the conditions read more semantically.
        (cond
          ;; This iteration came from the timeout channel. We already sent pending messages, so just recur.
          (not input?)                     (recur)
          ;; A nil came from the input channel, so the input channel was closed! Stop.
          (and input? (not ?next-message)) nil
          ;; Got a new input and the socket is open -- go ahead and send.
          (and ?next-message open?)        (do (apply send! ?next-message) (recur))
          ;; Got a new input but the socket isn't open -- queue it.
          ?next-message                    (do (vswap! pending conj ?next-message) (recur)))))
    input))

(defmethod ig/halt-key! ::sender [_ sender]
  (y/close! sender))

(defmethod ig/prep-key ::send! [_ config]
  (update config :timeout #(or % 5000)))

(defmethod system/post-init-spec ::send! [_]
  [:=> [:cat ::sente/event ifn?] :any])

(defmethod ig/init-key ::send! [_ {:keys [sender timeout]}]
  (fn send! [event-kwd body reply-fn]
    (y/put! sender [[event-kwd body] timeout reply-fn])))

(def config
  {::client {:packer (ig/ref ::sente/packer)
             :client-options (ig/ref ::client-options)}
   ::handle-event! {}
   ::handler {:handle-event! (ig/ref ::handle-event!)
              :client (ig/ref ::client)
              :context (ig/ref :factor/context)}
   ::sender {:client (ig/ref ::client)}
   ::send! {:sender (ig/ref ::sender)}})
