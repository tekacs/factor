(ns factor.client.y.map
  (:require ["yjs" :as yjs])
  (:refer-clojure :exclude [type assoc! get contains? count run! keys vals]))

;; https://docs.yjs.dev/api/shared-types/y.map

(def type
  yjs/Map)

(defn create
  []
  (yjs/Map.))

(defn assoc!
  [^js ymap key value]
  (.set ymap key value))

(defn get
  ([^js ymap key]
   (get ymap key nil))
  ([^js ymap key default]
   ;; Y.js returns undefined when not present, but we coerce to nil.
   ;; If being unable to distinguish that becomes an issue, this can be changed later.
   (or (.get ymap key) default)))

(defn delete!
  [^js ymap key]
  (.delete ymap key))

(defn contains?
  [^js ymap key]
  (.has ymap key))

(defn count
  [^js ymap]
  (.-size ymap))

(defn run!
  [^js ymap f]
  (.forEach ymap (fn [k v] (f [k v]))))

(defn keys
  [^js ymap]
  (.keys ymap))

(defn vals
  [^js ymap]
  (.values ymap))
