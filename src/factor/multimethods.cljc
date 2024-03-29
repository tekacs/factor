(ns factor.multimethods
  (:require #?(:clj [factor.functions :as functions])
            #?(:clj [factor.maps :as maps])
            #?(:clj [methodical.core :as methodical])))

#?(:clj
   (defn clojure-multimethod
     ([dispatch-fn] (clojure-multimethod dispatch-fn {}))
     ([dispatch-fn {:keys [meta cache hierarchy default-value prefers methods-map] :or {cache (methodical/simple-cache)}}]
      (methodical/multifn
       (methodical/standard-multifn-impl
        (methodical/clojure-method-combination)
        (functions/mapply methodical/standard-dispatcher dispatch-fn (maps/compact-map {:hierarchy hierarchy :default-value default-value :prefers prefers}))
        (apply methodical/clojure-method-table (filter some? [methods-map])))
       meta
       cache))))

#?(:clj
   (defn add-methods
     ([target primary-methods] (add-methods target primary-methods nil))
     ([target primary-methods aux-methods]
      (as-> target $
        (reduce (fn [target [dispatch-value f]] (methodical/add-primary-method target dispatch-value f)) $ primary-methods)
        (reduce (fn [target [qualifier dispatch-map]]
                  (reduce (fn [target [dispatch-value f]] (methodical/add-aux-method target qualifier dispatch-value f)) target dispatch-map)) $ aux-methods)))))

#?(:clj
   (defn merge-method-tables [target source]
     (let [source-primary (methodical/primary-methods source)
           source-aux (methodical/aux-methods source)]
       (add-methods target source-primary source-aux))))

(comment
  (let [x-fn (clojure-multimethod :x {:methods-map {0 (constantly 99)}})
        x-fn (methodical/add-primary-method x-fn 1 (fn [_] 0))
        y-fn (clojure-multimethod :x)
        y-fn (methodical/add-primary-method y-fn 1 (fn [_] 1))
        y-fn (methodical/add-primary-method y-fn 2 (fn [_] 2))
        combo (merge-method-tables x-fn y-fn)]
      [(combo {:x 0}) (combo {:x 1}) (combo {:x 2})]))


(defn map-multimethod [dispatch-fn dispatch-map]
  (fn [& args]
    (let [discriminator (apply dispatch-fn args)
          handler (get dispatch-map discriminator (:default dispatch-map))]
      (if handler
        (apply handler args)
        (throw (ex-info (str "No method in multimethod for dispatch value " (pr-str discriminator)) {:dispatch-value discriminator}))))))
