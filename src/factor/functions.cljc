(ns factor.functions
  (:require [factor.types :as ty :refer [=>]]))

(ty/defn discard-args
  [f] [ifn? => ifn?]
  (fn [& _]
    (f)))
