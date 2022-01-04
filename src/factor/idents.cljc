(ns factor.idents
  (:require [clojure.string :as string]
            [factor.types :as ty :refer [=>]]))

(ty/defn ident->dotted-form
  [kwd] [ident? => string?]
  (string/join "." (filterv some? [(namespace kwd) (name kwd)])))
