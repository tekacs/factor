(ns factor.pathom.server
  (:require [com.wsscode.pathom3.cache :as p.cache]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
            [com.wsscode.pathom3.connect.foreign :as pcf]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.operation.transit :as pcot]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.connect.runner :as pcr]
            [com.wsscode.pathom3.error :as p.error]
            [com.wsscode.pathom3.format.eql :as pf.eql]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.path :as p.path]
            [com.wsscode.pathom3.plugin :as p.plugin]
            [integrant.core :as ig]))

;; Based on https://pathom3.wsscode.com/docs/tutorials/serverless-pathom-gcf/

(defmethod ig/init-key ::resolvers [_ resolvers]
  resolvers)

(defmethod ig/init-key ::plan-cache$ [_ _]
  ;; TODO: Switch to an LRU cache, like https://pathom3.wsscode.com/docs/cache/#using-corecache
  (atom {}))

(defmethod ig/init-key ::env [_ {:keys [plan-cache$ resolvers]}]
  (pci/register {::pcp/plan-cache* plan-cache$} resolvers))

(defmethod ig/init-key ::boundary-interface [_ {:keys [env]}]
  (p.eql/boundary-interface env))

(defmethod ig/init-key ::sente-handler [_ {:keys [boundary-interface request-keys]}]
  (fn [context {[_ query] :event :keys [?reply-fn ring-req]}]
    (?reply-fn (boundary-interface (merge context (select-keys ring-req request-keys)) query))))

(def config
  {[::plan-cache$ ::default] {}
   [::env ::default] {:plan-cache$ (ig/ref [::plan-cache$ ::default])
                     :resolvers (ig/ref [::resolvers ::default])}
   [::boundary-interface ::default] {:env (ig/ref [::env ::default])}
   [::sente-handler ::default] {:boundary-interface (ig/ref [::boundary-interface ::default])}})
