(ns factor.functions
  (:require [factor.types :as ty :refer [=>]]))

(ty/defn discard-args
  [f] [::ty/fn => ::ty/fn]
  (fn [& _]
    (f)))
