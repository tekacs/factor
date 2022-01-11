(ns factor.pathom.client
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
            [factor.async :as async]
            #?(:cljs [factor.debugging :as debugging])
            [factor.pathom :as pathom]
            [factor.promises :as promises]
            [factor.system :as system]
            [factor.types :refer [=>] :as ty]
            [integrant.core :as ig]
            [malli.core :as m]
            [promesa.core :as pc]
            [taoensso.timbre :as timbre]))

(defmethod system/pre-init-spec ::sente-client [_]
  [:map
   [:sente-send! [:=> [:cat ::pathom/request ifn?] :any]]
   [:lenient-mode? {:optional true} [:maybe :boolean]]])

(defmethod ig/prep-key ::sente-client [_ config]
  ;; #(and false true) => false, to allow this to be set to false
  (update config :lenient-mode? (some-fn nil? true?)))

(defmethod ig/init-key ::sente-client [_ {:keys [sente-send! lenient-mode?]}]
  {:send!
   (fn send! [[channel entity query] reply-fn]
     (let [body (if (m/validate ::pathom/ast query) {:pathom/ast query} {:pathom/eql query})
           body (assoc body :pathom/entity entity :pathom/lenient-mode? lenient-mode?)]
       (sente-send! channel body reply-fn)))})

(def config
  {::sente-client {:sente-send! (ig/ref :factor.sente.client/send!)}})
