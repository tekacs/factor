(ns factor.identifiers
  #?(:clj (:import (java.util UUID)))
  (:require [factor.types :as ty :refer [=>]]))

(ty/defn random-uuid
  "Generate a UUID in a cross-platform way."
  [] [=> :error/id]
  #?(:clj (UUID/randomUUID)
     :cljs (random-uuid)))
