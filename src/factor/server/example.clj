(ns factor.server.example
  (:gen-class)
  (:require [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [factor.encoding :as encoding]
            [factor.pathom.server :as pathom-server]
            [factor.sente :as sente]
            [factor.sente.server :as sente-server]
            [factor.server.http :as http]
            [factor.server.injection :as injection]
            [factor.server.repl :as repl]
            [factor.server.routing :as routing]
            [factor.server.sente-http :as sente-http]
            [factor.system :as system]
            [factor.system.state :as state]
            [methodical.core :as methodical]))

(methodical/defmethod injection/init-key :factor/context [_ config _] config)

(defn config []
  (assoc
   (merge repl/config encoding/config pathom-server/config sente/config sente-http/config sente-server/config routing/config http/config)
   :factor/context {:nrepl-server (injection/ref ::repl/nrepl-server)}
   :factor.environment/profile :development

   [::pathom-server/resolvers ::pathom-server/default] [(pbir/constantly-fn-resolver ::now (fn [_] (java.util.Date.)))]
   ::sente-server/handle-event! {:dispatch-map {:factor/default (injection/ref [::pathom-server/sente-handler ::pathom-server/default])}}
   ::routing/cors-configuration {:origins ["http://localhost:3000"]}))

(defn -main []
  ;; There are a few configurations that the library consumer must set itself.
  ;; - `:factor/context` should be overridden as above and therefore should use whatever config is appropriate.
  ;; - `:factor.environment/profile` should be set to `:development`, `:production` or `:test`
  ;; - `:factor.server.routing/cors-configuration` should be set to a list of regexes of accepted CORS Origins.
  (system/set-prep! (fn [] (#'config)))

  ;; Get the application started -- first by loading namespaces and then starting up.
  (system/load-namespaces)
  (system/go))

(comment
  (-main)

  ;; Reload config
  (system/prep)

  ;; Stop the application.
  (system/halt)

  ;; Suspend, reload namespaces using tools.namespace.repl and then Resume.
  (system/reset)

  ;; Suspend, reload every namespace possible on disk using t.n.repl and then Resume.
  (system/reset-all)

  ;; Hard reset the system
  (system/hard-reset)

  ;; The current system config is in `system.state/config` -- may contain secrets in production!
  state/config

  ;; The current system map itself is in `system.state/system`.
  state/system
  )
