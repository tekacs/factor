(ns factor.client.y.doc
  (:refer-clojure :exclude [type get])
  (:require ["yjs" :as yjs]))

;; API docs at: https://docs.yjs.dev/api/y.doc

(def type
  yjs/Doc)

(defn create
  []
  (yjs/Doc.))

(defn client-id
  [doc]
  (.-clientID doc))

(defn enable-garbage-collection!
  [^js doc]
  (set! (.-gc doc) true))

(defn disable-garbage-collection!
  [^js doc]
  (set! (.-gc doc) false))

(defn transact!
  ([^js doc f]
   (.transact doc f))
  ([^js doc f origin]
   (.transact doc f origin)))

(defn get
  [^js doc key shared-type-constructor]
  (.get doc key shared-type-constructor))

(defn destroy!
  [^js doc]
  (.destroy doc))

(defn listen
  [^js doc event-name f]
  (.on doc event-name f))

(defn unlisten
  [^js doc event-name f]
  (.off doc event-name f))

(defn listen-once
  [^js doc event-name f]
  (.once doc event-name f))

(def =before-transaction "beforeTransaction")
(def =before-observer-calls "beforeObserverCalls")
(def =after-transaction "afterTransaction")
(def =on-update "update")
(def =on-subdocs "subdocs")
(def =on-destroy "destroy")
