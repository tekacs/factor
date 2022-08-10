(ns factor.server.nats
  (:require [factor.server.injection :as injection]
            [methodical.core :as methodical])
  (:import
   (io.nats.client Connection Dispatcher Nats MessageHandler)))

(methodical/defmethod injection/init-key ::connection [_ {:keys [jetstream-options]} _]
  (let [before     (System/currentTimeMillis)
        connection (if jetstream-options (Nats/connect jetstream-options) (Nats/connect))
        after      (System/currentTimeMillis)]
    (println "Took" (- after before) "ms to connect to NATS")
    connection))

(methodical/defmethod injection/halt-key! ::connection [_ connection _]
  (.close connection))

(methodical/defmethod injection/init-key ::responder [_ {:keys [^Connection connection subjects handle-message]} _]
  (let [dispatcher (.createDispatcher connection (reify MessageHandler (onMessage [this message] (handle-message connection message))))]
    (doseq [subject subjects] (.subscribe dispatcher subject))
    {:connection connection
     :dispatcher dispatcher
     :subjects subjects}))

(methodical/defmethod injection/halt-key! ::responder [_ {:keys [^Connection connection ^Dispatcher dispatcher subjects]} _]
  (doseq [subject subjects] (.unsubscribe dispatcher subject))
  (.closeDispatcher connection dispatcher))

(def config
  {::connection nil})
