(ns factor.debugging
  (:require [factor.types :as ty :refer [=>]]
            [fipp.edn :as fipp]))

(ty/defn fprint-str
  [value] [any? => string?]
  (with-out-str (fipp/pprint value)))

(ty/defn fprint
  [value] [any? => nil?]
  (println (fprint-str value)))
