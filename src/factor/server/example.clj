(ns factor.server.example
  (:require [factor.server.config :as config]
            [factor.system :as system]
            [factor.system.state :as state]
            [integrant.core :as ig]))

(comment
  ;; The library consumer must define `:factor/context`, with a definition that makes sense for the application.
  ;; It will be passed to all handers and used in many locations in the app -- so it should be customized.
  (defmethod ig/init-key :factor/context [_ _] {})

  ;; There are a few configurations that the library consumer must set itself.
  ;; - `:factor/context` should be overridden as above and therefore should use whatever config is appropriate.
  ;; - `:factor.environment/profile` should be set to `:development`, `:production` or `:test` -- it will be set from `NODE_ENV` in ClojureScript
  ;; - `:factor.server.routing/cors-configuration` should be set to a list of regexes of accepted CORS Origins.
  (system/set-prep!
   (fn []
     (assoc factor.server.config/template
            :factor/context {}
            :factor.environment/profile :development
            :factor.server.routing/cors-configuration [#"https://tekacs.com"])))

  ;; Get the application started -- first by loading namespaces and then starting up.
  (system/load-namespaces)
  (system/go)

  ;; Stop the application.
  (system/halt)

  ;; Suspend, reload namespaces using tools.namespace.repl and then Resume.
  (system/reset)

  ;; Suspend, reload every namespace possible on disk using t.n.repl and then Resume.
  (system/reset-all)

  ;; The current system config is in `system.state/config` -- may contain secrets in production!
  state/config

  ;; The current system map itself is in `system.state/system`.
  state/system
  )
