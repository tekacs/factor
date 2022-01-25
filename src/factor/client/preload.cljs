(ns factor.client.preload
  {:client/npm-deps ["react-refresh"]}
  (:require [com.tekacs.access :as a]
            [helix.experimental.refresh :as refresh]
            [integrant.core :as ig]))

(refresh/inject-hook!)

(defn refresh! []
  (let [log (a/get js/console :log)]
    (a/assoc! js/console :log (constantly nil))
    (refresh/refresh!)
    (a/assoc! js/console :log log)))

(defmethod ig/suspend-key! :factor.client.react/render [_ st]
  st)

(defmethod ig/resume-key :factor.client.react/render [_ _ _ st]
  ;; NOTE: This implementation does NOT respect changes to config -- it just does a hot reload.
  (refresh!)
  st)
