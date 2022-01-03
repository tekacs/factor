(ns factor.server.repl
  (:require [factor.server.metaprogramming :as metaprogramming]
            [integrant.core :as ig]
            [integrant.repl :as ir]
            [integrant.repl.state :as irs]))

(defn set-prep!
  [config-loader-fn]
  (ir/set-prep! (comp ig/prep config-loader-fn)))

(def config irs/config)
(metaprogramming/link-vars #'irs/config #'config)

(def system irs/system)
(metaprogramming/link-vars #'irs/system #'system)

(def preparer irs/preparer)
(metaprogramming/link-vars #'irs/preparer #'preparer)

(defn prep []
  (ir/prep))

(defn go []
  (ir/go))

(defn halt []
  (ir/halt))

(defn suspend []
  (ir/suspend))

(defn resume []
  (ir/resume))

(defn reset []
  (ir/reset))

(defn reset-all []
  (ir/reset-all))  

(comment
  (defmethod ig/init-key :factor/context [_ _] {})
  (set-prep! (fn [] (assoc factor.server.config/template :factor/context {})))
  (go)
  (halt)
  (reset-all)
  config
  system
  )
