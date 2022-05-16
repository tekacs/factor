(ns factor.client.y.array
  (:require ["yjs" :as yjs])
  (:refer-clojure :exclude [type count get run!]))

;; https://docs.yjs.dev/api/shared-types/y.array

(def type
  yjs/Array)

(defn create
  []
  (yjs/Array.))

(defn from
  [content]
  (yjs/Array.from content))

(defn count
  [^js yarr]
  (.-length yarr))

(defn assoc-slice!
  [^js yarr idx content-slice]
  (.insert yarr idx content-slice))

(defn delete-slice!
  [^js yarr idx length]
  (.delete yarr idx length))

(defn into!
  [^js yarr content-slice]
  (.push yarr content-slice))

(defn unshift-slice!
  [^js yarr content-slice]
  (.unshift yarr content-slice))

(defn get
  ([^js yarr idx]
   (get yarr idx nil))
  ([^js yarr idx default]
   ;; Y.js returns undefined when not present, but we coerce to nil.
   ;; If being unable to distinguish that becomes an issue, this can be changed later.
   (or (.get yarr idx) default)))

(defn slice
  ([yarr] (.slice yarr))
  ([yarr start] (.slice yarr start))
  ([yarr start end] (.slice yarr start end)))

(defn run-indexed!
  [^js yarr f]
  (.forEach yarr (fn [v i] (f [i v]))))

(defn run!
  [^js yarr f]
  (.forEach yarr (fn [v _] (f v))))

(defn mapa-indexed
  [^js yarr f]
  (.map yarr (fn [v i] (f i v))))

(defn mapa
  [^js yarr f]
  (.map yarr (fn [v _] (f v))))
