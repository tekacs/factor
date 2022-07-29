(ns factor.client.example
  (:require [factor.client.config :as config]
            [factor.client.react :as react]
            [factor.client.routing :as client-routing]
            [factor.encoding :as encoding]
            [factor.environment]
            [factor.pathom.client :as pathom-client]
            [factor.sente :as sente]
            [factor.sente.client :as sente-client]
            [factor.system :as system]
            [factor.system.state :as state]
            [helix.hooks :as hook]
            [integrant.core :as ig]))

;; Note the configuration in shadow-cljs.edn
;; Notably, using `factor.client.preload` in `[the-build :devtools :preloads]` drives `react-refresh` for hot reloading.

(react/prop ExampleComponent)
(react/defnc ExampleComponent [_]
  (let [[state-random _] (hook/use-state (rand))]
    (react/$ :div ["p-2"] "This is a random number from state: " state-random " (preserved by react-refresh), and on render: " (rand))))

(def config
  (merge
   encoding/config sente/config sente-client/config pathom-client/config client-routing/config
   {:factor/context {}
    ::sente-client/client-options
    {:protocol :http
     :host "localhost"
     :port (js/Number.parseInt config/PORT)
     :path "/api"}
    ::react/render
    {:target "root"
     :component ExampleComponent}
    ::client-routing/routes
    [["/" ::home]]}))

(defmethod ig/init-key :factor/context [_ _] {})

(defn ^:export main []
  (system/set-prep! (fn [] config))
  (system/go))

(comment
  ;; Reload config
  (system/prep)

  ;; Stop the application.
  (system/halt)

  ;; Suspend and then resume.
  (system/reset)

  ;; Halt and then init.
  (system/hard-reset)

  ;; The current system config is in `system.state/config` -- may contain secrets in production!
  @state/config$

  ;; The current system map itself is in `system.state/system`.
  @state/system$
  )
