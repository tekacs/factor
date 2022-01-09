(ns factor.server.sente-http
  (:require [factor.server.injection :as injection]
            [methodical.core :as methodical]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]))

(methodical/defmethod injection/init-key ::http-server-adapter [_ _ _]
  (get-sch-adapter))

(def config
  {::http-server-adapter {}})
