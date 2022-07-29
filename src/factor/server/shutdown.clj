(ns factor.server.shutdown
  (:require [factor.types :as ty :refer [=>]]))

(ty/defn add-shutdown-hook!
  [f] [ifn? => :any]
  (let [runtime (Runtime/getRuntime)]
    (.addShutdownHook runtime (Thread. f))))
