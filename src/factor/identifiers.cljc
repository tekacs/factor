(ns factor.identifiers
  #?(:cljs (:refer-clojure :exclude [random-uuid]))
  #?(:clj (:import (java.util UUID)))
  (:require [factor.types :as ty :refer [=>]]))

(ty/defn random-uuid
  "Generate a UUID in a cross-platform way."
  [] [=> :uuid]
  #?(:clj (UUID/randomUUID)
     :cljs (cljs.core/random-uuid)))

(ty/defn read-uuid
  [string] [:string => :uuid]
  #?(:clj (UUID/fromString string)
     :cljs (uuid string)))
