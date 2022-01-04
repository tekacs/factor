(ns factor.collections
  (:require [com.rpl.specter :as sr]
            [factor.types :as ty :refer [=>]]
            [taoensso.encore :as encore]))

(ty/defn positions
  [pred coll] [::ty/fn [:sequential any?] => [:sequential int?]]
  (keep-indexed
   (fn [idx v] (when (pred v) idx))
   coll))

(comment (positions even? [1 2 3 4 5]) => '(1 3))

(ty/defn sole-index-of
  [pred coll] [::ty/fn [:sequential any?] => [:maybe int?]]
  (let [pos (positions pred coll)]
    (case (count pos)
      0 nil
      1 (first pos)
      (throw (ex-info "The predicate matched at more than one position in the sequence, use `positions` instead" {:positions pos})))))

(comment
  (sole-index-of even? [1 2 3]) => 1
  (sole-index-of odd? [1 2 3]) => throw
  )

(ty/defn rest-after
  [pred coll]
  (next (drop-while (comp not pred) coll)))

(ty/defn first-after
  [pred coll]
  (first (rest-after pred coll)))

(ty/defn rest-before
  [pred coll]
  (let [until-element (take-while (comp not pred) coll)]
    (when-not (= (count coll) (count until-element))
      (seq until-element))))

(ty/defn last-before
  [pred coll]
  (last (rest-before pred coll)))

(ty/defn distinct-by
  [into-coll keyfn coll] [coll? ::ty/fn coll? => coll?]
  (into into-coll (encore/xdistinct keyfn) coll))

(defn remove-at-index
  [coll idx]
  (sr/setval idx sr/NONE coll))

(defn insert-before-index
  [coll idx value]
  (sr/setval (sr/before-index idx) value coll))

(defn insert-after-index
  [coll idx value]
  (sr/setval (sr/before-index (inc idx)) value coll))

(defn remove-value-in
  [coll value]
  (sr/setval [sr/ALL (sr/pred= value)] sr/NONE coll))

(defn replace-value-in
  [coll value new-value]
  (sr/setval [sr/ALL (sr/pred= value)] new-value coll))

(defn update-value-in
  [coll value update-fn]
  (sr/transform [sr/ALL (sr/pred= value)] update-fn coll))
