(ns factor.client.javascript
  (:require [cljs-bean.core :refer [bean ->clj]]
            [com.tekacs.access :as a])
  (:require-macros [factor.client.javascript :refer [*fn]]))

(defn clj->bound-js
  "Like clj->js, but shallow -- and binds functions, setting their `this` to the result object."
  [m]
  (let [result #js {}]
    (doseq [[k v] m]
      (let [v (if (fn? v) (. v bind result) v)]
        (a/assoc!+ result k v)))
    result))

(defn args->bean
  [f]
  (fn [& args]
    (apply f (map bean args))))

(defn args->clj
  [f]
  (fn [& args]
    (apply f (map ->clj args))))