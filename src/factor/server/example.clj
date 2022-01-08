(ns factor.server.example
  (:gen-class)
  (:require [factor.server.http :as http]
            [factor.server.injection :as injection]
            [factor.server.repl :as repl]
            [factor.server.routing :as routing]
            [factor.server.sente :as sente]
            [factor.system :as system]
            [factor.system.state :as state]
            [methodical.core :as methodical]))

(methodical/defmethod injection/init-key :factor/context [_ config _] config)

(defn config []
  (assoc
   (merge repl/config sente/config routing/config http/config)
   :factor/context {:nrepl-server (injection/ref ::repl/nrepl-server)}
   :factor.environment/profile :development
   ::routing/cors-configuration [#"https://tekacs.com"]))

(defn -main []
  ;; There are a few configurations that the library consumer must set itself.
  ;; - `:factor/context` should be overridden as above and therefore should use whatever config is appropriate.
  ;; - `:factor.environment/profile` should be set to `:development`, `:production` or `:test` -- it will be set from `NODE_ENV` in ClojureScript
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
