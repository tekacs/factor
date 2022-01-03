(ns factor.server.metaprogramming)

;; From https://github.com/clj-commons/potemkin/blob/3e404364ae2fd32f7a53b362a79d2012ab958ab2/src/potemkin/namespaces.clj
(defn link-vars
  "Makes sure that all changes to `src` are reflected in `dst`."
  [src dst]
  (add-watch src dst
    (fn [_ src _old _new]
      (alter-var-root dst (constantly @src))
      (alter-meta! dst merge (dissoc (meta src) :name)))))
