(ns factor.client.preload
  {:client/npm-deps ["react-refresh"]}
  (:require [com.tekacs.access :as a]
            [helix.experimental.refresh :as r]))

(r/inject-hook!)

(defn ^:dev/after-load refresh []
  (let [log (a/get js/console :log)]
    (a/assoc! js/console :log (constantly nil))
    (r/refresh!)
    (a/assoc! js/console :log log)))
