(ns factor.client.dom
  (:require [com.tekacs.access :as a]))

(defn get-element-by-id [id]
  (a/document! :getElementById id))

(defn closest-data [elem data-key]
  (when-let [data-elem (a/call! elem :closest (str "[data-" (name data-key) "]"))]
    (a/get-in+ data-elem [:dataset data-key])))

(defn all-enclosing-data [elem data-key]
  (let [data-selector   (str "[data-" (name data-key) "]")
        gather-elements (fn gather-elements
                          [current elements]
                          (if-let [next-closest (and current (a/call! current :closest data-selector))]
                            (recur (a/get next-closest :parentElement) (conj elements next-closest))
                            elements))]
    (->> (a/get-in+ elem [:dataset data-key])
         (for [elem (gather-elements elem '())])
         (reverse))))

(defn query-selector [sel]
  (a/document! :querySelector sel))
