(ns factor.generators
  (:require [factor.types :as ty :refer [=>]]
            [malli.generator :as mg]))

(ty/defn generate-seeded
  [id-key type id] [[:maybe qualified-keyword?] qualified-keyword? any? => some?]
  (cond->
   (mg/generate type {:seed (hash id)})
    (some? id-key) (assoc id-key id)))
