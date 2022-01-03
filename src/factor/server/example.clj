(ns factor.server.example
  (:require [factor.server.config :as config]
            [factor.server.repl :as repl]
            [integrant.core :as ig]))

(comment
  ;; The library consumer must define `:factor/context`, with a definition that makes sense for the application.
  ;; It will be passed to all handers and used in many locations in the app -- so it should be customized.
  (defmethod ig/init-key :factor/context [_ _] {})

  ;; There are a few configurations that the library consumer must set itself.
  ;; - `:factor/context` should be overridden as above and therefore should use whatever config is appropriate.
  ;; - `:factor.system/profile` should be set to `:development`, `:production` or `:test` -- it will be set from `NODE_ENV` in ClojureScript
  ;; - `:factor.server.routing/cors-configuration` should be set to a list of regexes of accepted CORS Origins.
  (repl/set-prep!
   (fn []
     (assoc factor.server.config/template
            :factor/context {}
            :factor.system/profile :development
            :factor.server.routing/cors-configuration [#"https://tekacs.com"])))

  ;; Get the application started -- first by loading namespaces and then starting up.
  (repl/load-namespaces)
  (repl/go)

  ;; Stop the application.
  (repl/halt)

  ;; Suspend, reload namespaces using tools.namespace.repl and then Resume.
  (repl/reset)

  ;; Suspend, reload every namespace possible on disk using t.n.repl and then Resume.
  (repl/reset-all)

  ;; The current system config is in `repl/config` -- may contain secrets in production!
  repl/config

  ;; The current system map itself is in `repl/system`.
  repl/system
  )
