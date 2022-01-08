(ns factor.server.injection
  (:refer-clojure :exclude [ref])
  (:require [integrant.core :as ig]
            [methodical.core :as methodical]))

(methodical/defmulti resolve-key (fn [key value prev] (#'ig/normalize-key key)))
(methodical/defmethod resolve-key :default [_ v _] v)
(defmethod ig/resolve-key :default [key value] (resolve-key key value nil))

(methodical/defmulti prep-key (fn [key value prev] (#'ig/normalize-key key)))
(methodical/defmethod prep-key :default [_ v _] v)
(defmethod ig/prep-key :default [key value] (prep-key key value nil))

(methodical/defmulti init-key (fn [key value prev] (#'ig/normalize-key key)))
(defmethod ig/init-key :default [key value] (init-key key value nil))

(methodical/defmulti halt-key! (fn [key value prev] (#'ig/normalize-key key)))
(methodical/defmethod halt-key! :default [_ _ _])
(defmethod ig/halt-key! :default [key value] (halt-key! key value nil))

(methodical/defmulti resume-key (fn [key value old-value old-impl prev] (#'ig/normalize-key key)))
(methodical/defmethod resume-key :default [k v _ _ _] (ig/init-key k v))
(defmethod ig/resume-key :default [key value old-value old-impl] (resume-key key value old-value old-impl nil))

(methodical/defmulti suspend-key! (fn [key value prev] (#'ig/normalize-key key)))
(methodical/defmethod suspend-key! :default [k v _] (ig/halt-key! k v))
(defmethod ig/suspend-key! :default [key value] (suspend-key! key value nil))

;; These functions are duplicated here from integrant to make it easier to spot when that import is mistakenly used.
;; Having these here mostly obviates the need to import `integrant.core` in the codebase.
(defn ref
  "Create a reference to a top-level key in a config map."
  [key]
  (ig/ref key))

(defn refset
  "Create a set of references to all matching top-level keys in a config map."
  [key]
  (ig/refset key))