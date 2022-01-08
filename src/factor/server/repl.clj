(ns factor.server.repl
  (:require [cider.nrepl]
            [factor.server.injection :as injection]
            [integrant.core :as ig]
            [methodical.core :as methodical]
            [nrepl.server]
            [taoensso.timbre :as timbre]))

(methodical/defmethod injection/prep-key ::nrepl-server [_ config]
  (-> config
      (update :host #(or % "127.0.0.1"))
      (update :port #(or % (+ 1024 (rand-int (- 65536 1024)))))))

(methodical/defmethod injection/init-key ::nrepl-server [_ {:keys [host port]}]
  (let [server (nrepl.server/start-server
                :host host
                :port port
                :handler cider.nrepl/cider-nrepl-handler)]
    (timbre/info "nREPL server started on port" port "on host" host "-" (str "nrepl://" host ":" port))
    server))

;; NOTE: We don't restart the nrepl server at all during suspend/resume.
(methodical/defmethod injection/suspend-key! ::nrepl-server [_ _])
(methodical/defmethod injection/resume-key ::nrepl-server [_ config old-config server]
  (if (= config old-config)
    server
    (ig/init-key ::nrepl-server config)))

(methodical/defmethod injection/halt-key! ::nrepl-server
  [_ server]
  (nrepl.server/stop-server server))

(def config
  {::nrepl-server
   {:host nil
    :port nil}})
