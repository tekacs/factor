(ns factor.client.zustand
  (:require ["zustand" :default createReactStore]
            ["zustand/shallow" :default shallow]
            ["zustand/vanilla" :default createVanillaStore]
            [factor.client.types :as fct]
            [factor.types :refer [=>] :as ty]))

(ty/def ::vanilla-store
  [::fct/js-obj
   [:getState ifn?]
   [:setState ifn?]
   [:subscribe ifn?]
   [:destroy ifn?]])
(ty/def ::store-hook [:and ifn? ::vanilla-store])
(ty/def ::store [:or ::vanilla-store ::store-hook])

(defn shallow=
  [a b]
  (cond
    (identical? a b) true

    (or (nil? a) (nil? b)) false

    (and (map? a) (map? b))
    (and (= (keys a) (keys b))
         (every? #(identical? (get a %) (get b %)) (keys a)))

    (and (vector? a) (vector? b))
    (and (= (count a) (count b))
         (every? #(identical? (get a %) (get b %)) (range (count a))))

    :else
    (shallow a b)))

;; The `set` function passed to store-fn takes the forms:
;; - state => value-to-merge-into-state
;; - state, overwrite? (true) => state
;; Since this module is likely to use CLJS data, Zustand's merging won't work.
(defn- set-wrapper
  [set-fn]
  (fn wrapped-zustand-setter
    [state-transform-fn-or-value]
    (set-fn state-transform-fn-or-value false)))

(ty/defn vanilla-store
  ([store-fn] [ifn? => ::vanilla-store] (vanilla-store store-fn shallow=))
  ([store-fn equality-fn]
   [ifn? ifn? => ::vanilla-store]
   (createVanillaStore (fn [set _get] (store-fn (set-wrapper set))) equality-fn)))

(ty/defn react-store
  ([store-fn] [ifn? => ::store-hook] (react-store store-fn shallow=))
  ([store-fn equality-fn]
   [ifn? ifn? => ::store-hook]
   (createReactStore (fn [set _get] (store-fn (set-wrapper set))) equality-fn)))

(ty/defn destroy-store!
  [store] [::store => :any]
  (.destroy store))

(defn subscribe
  [store handler]
  (.subscribe store handler))

(defn state
  [store]
  (.getState store))

(defn set-state!
  [store transform-state-fn-or-value]
  ((set-wrapper (fn [fn-or-value merge?] (.setState store fn-or-value merge?))) transform-state-fn-or-value))

(comment
  (def store (vanilla-store (constantly {})))
  (set-state! store #(assoc % :x 1))
  (.setState store {:x 1} false)
  (js/console.log (state store))
  (js/console.log {})
  )
