(ns factor.client.events
  (:require [com.tekacs.access :as a]
            [factor.types :as ty :refer [=>]]))

(defn prevent-default!
  [ev]
  (a/call! ev :preventDefault)
  ev)

(defn stop-propagation!
  [ev]
  (a/call! ev :stopPropagation)
  ev)

(ty/defn ^{:aave.core/enforce-purity false} stop-everything-handler
  [handler-fn] [::ty/fn => ::ty/fn]
  (fn [ev]
    (handler-fn ev)
    (stop-propagation! ev)
    (prevent-default! ev)
    false))
