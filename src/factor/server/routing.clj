(ns factor.server.routing
  (:require [factor.encoding :as encoding]
            [factor.errors :as err]
            [factor.server.injection :as injection]
            [methodical.core :as methodical]
            [reitit.dev.pretty :as pretty]
            [reitit.http :as http]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart :as multipart]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.ring :as ring]
            [ring.middleware.cors :as cors]
            [ring.middleware.keyword-params])
  (:import java.util.UUID))

;; TODO: Switch from aleph/reitit to bidi/yada?

;; region Middleware

(methodical/defmethod injection/init-key ::exception-handlers [_ _]
  (assoc
   exception/default-handlers
   
   ;; all exceptions are passed through here first -- it calls downstream handlers.
   ::exception/wrap
   (fn [handler ex request]
     (let [id (UUID/randomUUID)]
       (err/log-exception id ex)
       (handler ex (assoc request :exception/id id))))

   ;; all other exceptions
   ::exception/default
   (fn [_ req] {:body {:error/type :exception/system :error/id (:exception/id req)}})))

(def keyword-params
  {:name  ::keyword-params
   :enter (fn [ctx] (update ctx :request ring.middleware.keyword-params/keyword-params-request))})

(def cors-interceptor
  {:name    ::cors
   :compile (fn [{:keys [access-control]} _]
              (when access-control
                (let [access-control (cors/normalize-config (mapcat identity access-control))]
                  {:enter (fn cors-interceptor-enter
                            [{:keys [request] :as ctx}]
                            (if (and (cors/preflight? request)
                                     (cors/allow-request? request access-control))
                              (let [resp (cors/add-access-control
                                           request
                                           access-control
                                           cors/preflight-complete-response)]
                                (assoc ctx
                                       :response resp
                                       :queue nil))
                              ctx))
                   :leave (fn cors-interceptor-leave
                            [{:keys [request response] :as ctx}]
                            (cond-> ctx
                              (and (cors/origin request)
                                   (cors/allow-request? request access-control)
                                   response)
                              (assoc :response
                                     (cors/add-access-control
                                       request
                                       access-control
                                       response))))})))})

;; endregion Middleware

(methodical/defmethod injection/init-key ::handlers [_ {:keys [_base-ctx sente-server]}]
  {::home           (constantly {:name ::home :body "Shoo"})
   ::echo           (fn [req] {:name ::echo :body (keys req)})
   ::headers        (fn [req] {:name ::headers :body (:headers req)})
   ::decode         (fn [req] {:name ::decode :body (:body-params req)})
   ::sente-get      (:get-and-ws-handler sente-server)
   ::sente-post     (:post-handler sente-server)})

(methodical/defmethod injection/init-key ::routes [_ {:keys [handlers]}]
  [["/" {:get (handlers ::home)}]
   ["/dev"
    ["/echo" {:get (handlers ::echo)}]
    ["/headers" {:get (handlers ::headers)}]
    ["/decode" {:post (handlers ::decode)}]]
   ["/api" {:get (handlers ::sente-get) :post (handlers ::sente-post)}]])

(methodical/defmethod injection/init-key ::cors-configuration [_ {:keys [origins]}]
  {:access-control-allow-origin      origins
   :access-control-allow-credentials "true"
   :access-control-allow-methods     [:get :put :post :delete]
   :access-control-allow-headers     ["content-type"
                                      "authorization"
                                      "x-csrf-token"
                                      "x-requested-with"]})

(methodical/defmethod injection/init-key ::router [_ {:keys [routes cors-configuration exception-handlers]}]
  (http/router
    routes
    {:exception pretty/exception
     ;; :reitit.interceptor/transform dev/print-context-diffs
     :data      {:muuntaja       encoding/muuntaja-instance
                 :access-control cors-configuration
                 :interceptors   [(parameters/parameters-interceptor)
                                  keyword-params
                                  (muuntaja/format-negotiate-interceptor)
                                  (muuntaja/format-response-interceptor)
                                  (exception/exception-interceptor exception-handlers)
                                  (muuntaja/format-request-interceptor)
                                  (multipart/multipart-interceptor)
                                  cors-interceptor]}}))

(methodical/defmethod injection/init-key ::handle [_ {:keys [router]}]
  (http/ring-handler
    router
    (ring/create-default-handler)
    {:executor sieppari/executor}))

(def config
  {;; XXX: This MUST be overridden by the user for production use.
   ;; Each origin should be a regex of the domain, as in the commented out example below.
   ::cors-configuration
   {:origins [#_#"https://tekacs.com"]}

   ::exception-handlers
   {}

   ::handlers
   {:context (injection/ref :factor/context)
    :sente-server (injection/ref :factor.server.sente/server)}

   ::routes
   {:handlers (injection/ref ::handlers)}

   ::router
   {:routes (injection/ref ::routes)
    :exception-handlers (injection/ref ::exception-handlers)
    :cors-configuration (injection/ref ::cors-configuration)}

   ::handle
   {:router (injection/ref ::router)}})
