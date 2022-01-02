(ns hooks.react
  (:require [clj-kondo.hooks-api :as api]))

;; Hooks for dealing with my React macros
;; lint-as wasn't cutting it any more :)

(defn prop [{:keys [node]}]
  (let [[sym _?type] (rest (:children node))]
    {:node (api/list-node (list (api/token-node 'declare) sym))}))
