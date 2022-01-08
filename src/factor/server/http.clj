(ns factor.server.http
  (:require [aero.core :as aero]
            [factor.server.injection :as injection]
            [methodical.core :as methodical]
            [org.httpkit.client]
            [org.httpkit.server :as hk]
            [org.httpkit.sni-client :as sni-client]))

;; Could be worth stealing the implementation from here: https://github.com/weavejester/integrant#suspending-and-resuming
;; ... or https://github.com/duct-framework/server.http.http-kit/blob/78b99aade8633b692aa8ee2da97caba55c8abe5d/src/duct/server/http/http_kit.clj

(methodical/defmethod injection/prep-key ::server [_ config]
  (update config :port #(or % (some-> (#'aero/get-env "PORT") Long/parseLong) 9090)))

(methodical/defmethod injection/init-key ::server [_ {:keys [port handler]}]
  (alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))
  (hk/run-server handler {:port port :legacy-return-value? false}))

(methodical/defmethod injection/halt-key! ::server [_ server]
  (some-> (hk/server-stop! server) deref))

(def config
  {::server
   {:port    nil ;; NOTE: See default value in ig/prep-key
    :handler (injection/ref :factor.server.routing/handle)}})
