(ns factor.functions
  (:require [factor.types :as ty :refer [=>]]))

(ty/defn discard-args
  [f] [ifn? => ifn?]
  (fn [& _]
    (f)))

(defn mapply
  "Like apply, but applies a map in the last position as keyword arguments instead to a function that takes [& {:keys [...]}]"
  [f & args]
  (apply f (apply concat (butlast args) (last args))))
