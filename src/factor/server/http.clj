(ns factor.server.http
  (:require [integrant.core :as ig]
            [org.httpkit.client]
            [org.httpkit.server :as hk]
            [org.httpkit.sni-client :as sni-client]))

(alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))

;; Could be worth stealing the implementation from here: https://github.com/weavejester/integrant#suspending-and-resuming
;; ... or https://github.com/duct-framework/server.http.http-kit/blob/78b99aade8633b692aa8ee2da97caba55c8abe5d/src/duct/server/http/http_kit.clj

(defmethod ig/init-key ::server [_ {:keys [port handler]}]
  (hk/run-server handler {:port port :legacy-return-value? false}))

(defmethod ig/halt-key! ::server [_ server]
  (some-> (hk/server-stop! server) deref))
