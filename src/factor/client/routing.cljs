(ns factor.client.routing
  (:require [integrant.core :as ig]
            [reitit.coercion.malli :as rcm]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [reitit.frontend.history :as rfh]))

(defmethod ig/init-key ::routes [_ routes]
  routes)

(defmethod ig/init-key ::last-match$ [_ _]
  (atom nil))

(defmethod ig/init-key ::router [_ {:keys [routes opts]}]
  (rf/router routes opts))

(defmethod ig/init-key ::history [_ {:keys [last-match$ router router-opts]}]
  (rfe/start! router (partial reset! last-match$) (merge {:use-fragment false} router-opts)))

(defmethod ig/halt-key! ::history [_ history]
  (rfh/stop! history))

(defn relative->absolute [path]
  (js/URL. path js/document.location))

(def config
  {::last-match$ {}
   ::router {:routes (ig/ref ::routes)}
   ::history {:last-match$ (ig/ref ::last-match$)
              :router (ig/ref ::router)}})
