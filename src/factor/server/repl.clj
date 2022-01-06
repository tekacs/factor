(ns factor.server.repl
  (:require [cider.nrepl]
            [integrant.core :as ig]
            [nrepl.server]
            [taoensso.timbre :as timbre]))

(defmethod ig/prep-key ::nrepl-server [_ config]
  (-> config
      (update :host #(or % "127.0.0.1"))
      (update :port #(or % (+ 1024 (rand-int (- 65536 1024)))))))

(defmethod ig/init-key ::nrepl-server [_ {:keys [host port]}]
  (let [server (nrepl.server/start-server
                :host host
                :port port
                :handler cider.nrepl/cider-nrepl-handler)]
    (timbre/info "nREPL server started on port" port "on host" host "-" (str "nrepl://" host ":" port))
    server))

;; NOTE: We don't restart the nrepl server at all during suspend/resume.
(defmethod ig/suspend-key! ::nrepl-server [_ _])
(defmethod ig/resume-key ::nrepl-server [_ config old-config server]
  (if (= config old-config)
    server
    (ig/init-key ::nrepl-server config)))

(defmethod ig/halt-key! ::nrepl-server
  [_ server]
  (nrepl.server/stop-server server))

(def config
  {:factor.server.repl/nrepl-server
   {:host nil
    :port nil}})