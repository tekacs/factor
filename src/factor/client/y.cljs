(ns factor.client.y)

;; TODO: Explore https://github.com/sep2/immer-yjs (but see https://github.com/sep2/immer-yjs/issues/1)
;; TODO: Explore https://syncedstore.org/ around Y.js

(defn doc-of
  [^js shared-type]
  (.-doc shared-type))

(defn parent-of
  [^js shared-type]
  (.-parent shared-type))

(defn json-of
  [^js shared-type]
  (.toJSON shared-type))

(defn observe!
  [^js shared-type f]
  (.observe shared-type f))

(defn unobserve!
  [^js shared-type f]
  (.unobserve shared-type f))

(defn observe-deep!
  [^js shared-type f]
  (.observeDeep shared-type f))

(defn unobserve-deep!
  [^js shared-type f]
  (.unobserveDeep shared-type f))

(defn clone!
  [^js shared-type]
  (.clone shared-type))
