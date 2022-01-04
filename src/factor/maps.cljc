(ns factor.maps
  (:require [factor.types :as ty :refer [=>]]))

(defn assoc-when [m key value]
  (if (some? value) (assoc m key value) m))

(ty/defn map-map
  [m map-fn] [map? ifn? => map?]
  (->> m (map (fn [[k v]] (map-fn k v))) (into {})))

(ty/defn filter-map
  [m filter-fn] [map? ifn? => map?]
  (->> m (filter (fn [[k v]] (filter-fn k v))) (into {})))

(ty/defn compact-map
  "Keep only the map keys where the values are non-nil"
  [m] [map? => [:map-of any? some?]]
  (filter-map m #(some? %2)))

(ty/defn select-ids
  [m] [[:map-of keyword? any?] => [:map-of keyword? any?]]
  (filter-map m #(= (name %1) "id")))

(ty/defn keys-matching-ns
  [m kwd] [map? keyword? => map?]
  (let [ns (namespace kwd)]
    (filter-map m #(and (keyword? %1) (= (namespace %1) ns)))))

(ty/defn only-updates
  "Of the values in `to-map`, only keep those that have changed from `from-map`
   `nil` values in `to-map` are kept if they differ from the position in `from-map`"
  [from-map to-map] [map? map? => map?]
  (filter-map to-map (fn [k v] (not= (get from-map k ::only-updates-sentinel) v))))

(ty/defn merge-paths
  "Merge a map into target-map using assoc-in instead of assoc-ing each key"
  [target-map updates]
  (->> updates
       (reduce (fn [m [k v]] (assoc-in m k v)) target-map)
       (into {})))

(comment (merge-paths {:x {:y 0}} {[:x :y] 1}) => {:x {:y 1}})

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))
