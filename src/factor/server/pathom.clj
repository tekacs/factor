(ns factor.server.pathom
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
            [factor.server.injection :as injection]
            [methodical.core :as methodical]))

;; Based on https://pathom3.wsscode.com/docs/tutorials/serverless-pathom-gcf/

(methodical/defmethod injection/init-key ::resolvers [_ resolvers]
  resolvers)

(methodical/defmethod injection/init-key ::plan-cache$ [_ _]
  ;; TODO: Switch to an LRU cache, like https://pathom3.wsscode.com/docs/cache/#using-corecache
  (atom {}))

(methodical/defmethod injection/init-key ::env [_ {:keys [plan-cache$ resolvers]}]
  (pci/register {::pcp/plan-cache* plan-cache$} resolvers))

(methodical/defmethod injection/init-key ::boundary-interface [_ {:keys [env]}]
  (p.eql/boundary-interface env))

(def config
  {::plan-cache$ {}
   ::env {:plan-cache$ (injection/ref ::plan-cache$)
          :resolvers (injection/ref ::resolvers)}
   ::boundary-interface {:env (injection/ref ::env)}})
