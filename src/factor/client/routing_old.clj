(ns factor.client.routing-old)

(defmacro route [path & content]
  `(factor.client.react/$ factor.client.routing/Route {:path (factor.client.routing/norm ~path)} ~@content))
