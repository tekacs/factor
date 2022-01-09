(ns factor.client.storage
  (:refer-clojure :exclude [remove])
  (:require ["localforage" :as localforage]
            [factor.keywords :as keywords]
            [factor.promises :as promises]
            [factor.types :refer [=>] :as ty]
            [promesa.core :as pc]))

(ty/def ::key [:or string? keyword?])

(defonce listeners$
  (atom {}))

(ty/defn stringify-key
  [key] [::key => :string]
  (cond
    (string? key)  key
    (keyword? key) (keywords/keyword->str key)))

(ty/defn add-listener!
  [k f] [::key ifn? => :any]
  (swap! listeners$ update (stringify-key k) #(conj (or % #{}) f)))

(ty/defn remove-listener!
  [k f] [::key ifn? => :any]
  (swap! listeners$ update (stringify-key k) disj f))

(ty/defn notify-listeners!
  [k]
  (doseq [f (get @listeners$ (stringify-key k))]
    (f k)))

(ty/defn store!
  [k v writer] [::key any? ifn? => ::promises/promise]
  (pc/do!
   (localforage/setItem (stringify-key k) (writer v))
   (notify-listeners! k)))

(ty/defn ^{:aave.core/enforce-purity false} load
  [k reader] [::key ifn? => ::promises/promise]
  (pc/chain (localforage/getItem (stringify-key k)) reader))

(ty/defn remove!
  [k] [::key => ::promises/promise]
  (pc/do!
   (localforage/removeItem (stringify-key k))
   (notify-listeners! k)))

(comment
  (pc/then (store! "test" {:key [:value "string"]} pr-str) js/console.log)
  (pc/then (load "test" clojure.edn/read-string) js/console.log)
  )
