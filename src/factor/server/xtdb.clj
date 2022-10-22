(ns factor.server.xtdb
  (:require
   [factor.server.injection :as injection]
   [methodical.core :as methodical]
   [xtdb.api :as xt]))

(methodical/defmethod injection/init-key ::node [_ config _]
  (xt/start-node config))

(methodical/defmethod injection/halt-key! ::node [_ node _]
  (.close node))

(def config
  {})
