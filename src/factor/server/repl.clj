(ns factor.server.repl
  (:require [cider.nrepl]
            [factor.server.injection :as injection]
            [factor.server.io :as io]
            [factor.server.shutdown :as shutdown]
            [integrant.core :as ig]
            [methodical.core :as methodical]
            [nrepl.server]
            [taoensso.timbre :as timbre]))

(defn- write-port-file!
  [{:keys [port port-file]}]
  (when-not (io/is-file? port-file)
    (spit port-file port)))

(defn delete-port-file-on-shutdown!
  [{:keys [port port-file]}]
  (shutdown/add-shutdown-hook!
   (fn []
     (when (and (io/is-file? port-file)
                (= (Long/parseLong (slurp port-file)) port))
       (io/delete! port-file)))))

(methodical/defmethod injection/prep-key ::nrepl-server [_ config _]
  (-> config
      (update :host #(or % "127.0.0.1"))
      (update :port #(or % (+ 1024 (rand-int (- 65536 1024)))))
      (update :port-file #(or % ".nrepl-port"))))

(methodical/defmethod injection/init-key ::nrepl-server [_ {:keys [host port port-file]} _]
  (let [server (nrepl.server/start-server
                :host host
                :port port
                :handler cider.nrepl/cider-nrepl-handler)]
    (timbre/info "nREPL server started on port" port "on host" host "-" (str "nrepl://" host ":" port))
    (delete-port-file-on-shutdown! {:port port :port-file port-file})
    (write-port-file! {:port port :port-file port-file})
    server))

;; NOTE: We don't restart the nrepl server at all during suspend/resume.
(methodical/defmethod injection/suspend-key! ::nrepl-server [_ _ _])
(methodical/defmethod injection/resume-key ::nrepl-server [_ config old-config server _]
  (if (= config old-config)
    server
    (ig/init-key ::nrepl-server config)))

(methodical/defmethod injection/halt-key! ::nrepl-server
  [_ server _]
  (nrepl.server/stop-server server))

(def config
  {::nrepl-server
   {:host nil
    :port nil}})
