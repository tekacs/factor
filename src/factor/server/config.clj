(ns factor.server.config
  (:require [factor.config]
            [integrant.core :as ig]))

(def sente
  {:factor.server.sente/server-options
   {:user-id-fn nil}

   :factor.server.sente/server
   {:options (ig/ref :factor.server.sente/server-options)}

   :factor.server.sente/handler
   {:server  (ig/ref :factor.server.sente/server)
    :context (ig/ref :factor/context)}})

(def routing
  {;; XXX: This MUST be overridden by the user for production use.
   ;; Each origin should be a regex of the domain, as in the commented out example below.
   :factor.server.routing/cors-configuration
   {:origins [#_ #"https://tekacs.com"]}

   :factor.server.routing/exception-handlers
   {}

   :factor.server.routing/handlers
   {:context (ig/ref :factor/context)
    :sente-server (ig/ref :factor.server.sente/server)}

   :factor.server.routing/routes
   {:handlers (ig/ref :factor.server.routing/handlers)}

   :factor.server.routing/router
   {:routes (ig/ref :factor.server.routing/routes)
    :exception-handlers (ig/ref :factor.server.routing/exception-handlers)
    :cors-configuration (ig/ref :factor.server.routing/cors-configuration)}

   :factor.server.routing/handle
   {:router (ig/ref :factor.server.routing/router)}})

(def http
  {:factor.server.http/server
   {:port    nil ;; NOTE: See default value in ig/prep-key
    :handler (ig/ref :factor.server.routing/handle)}})

(def template
  (merge sente routing http))
