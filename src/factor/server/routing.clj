(ns factor.server.routing
  (:require [factor.errors :as err]
            [factor.server.injection :as injection]
            [methodical.core :as methodical]
            [reitit.dev.pretty :as pretty]
            [reitit.http :as http]
            [reitit.http.interceptors.dev :as dev]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart :as multipart]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.ring :as ring]
            [ring.middleware.keyword-params]
            [simple-cors.reitit.interceptor :as cors])
  (:import java.util.UUID))

;; TODO: Switch from aleph/reitit to bidi/yada?

;; region Middleware

(methodical/defmethod injection/init-key ::exception-handlers [_ _ _]
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

;; endregion Middleware

(methodical/defmethod injection/init-key ::handlers [_ {:keys [_base-ctx sente-server]} _]
  {::home           (constantly {:name ::home :body "Shoo"})
   ::echo           (fn [req] {:name ::echo :body (keys req)})
   ::headers        (fn [req] {:name ::headers :body (:headers req)})
   ::decode         (fn [req] {:name ::decode :body (:body-params req)})
   ::sente-get      (:get-and-ws-handler sente-server)
   ::sente-post     (:post-handler sente-server)})

(methodical/defmethod injection/init-key ::routes [_ {:keys [handlers]} _]
  [["/" {:get (handlers ::home)}]
   ["/dev"
    ["/echo" {:get (handlers ::echo)}]
    ["/headers" {:get (handlers ::headers)}]
    ["/decode" {:post (handlers ::decode)}]]
   ["/api" {:get (handlers ::sente-get) :post (handlers ::sente-post)}]])

(methodical/defmethod injection/init-key ::cors-configuration [_ {:keys [origins]} _]
  {:cors-config {:origins origins
                 :allow-credentials? true
                 :max-age 300
                 :allowed-request-methods [:get :put :post :delete]
                 :allowed-request-headers ["content-type"
                                           "authorization"
                                           "x-csrf-token"
                                           "x-requested-with"]}})

(methodical/defmethod injection/init-key ::router [_ {:keys [routes muuntaja-instance cors-configuration exception-handlers]} _]
  (http/router
   routes
   {:exception pretty/exception
    ;; :reitit.interceptor/transform dev/print-context-diffs
    :reitit.http/default-options-endpoint (cors/make-default-options-endpoint cors-configuration)
    :data      {:muuntaja       muuntaja-instance
                :access-control cors-configuration
                :interceptors   [(parameters/parameters-interceptor)
                                 keyword-params
                                 (muuntaja/format-negotiate-interceptor)
                                 (muuntaja/format-response-interceptor)
                                 (exception/exception-interceptor exception-handlers)
                                 (muuntaja/format-request-interceptor)
                                 (multipart/multipart-interceptor)
                                 (cors/cors-interceptor cors-configuration)]}}))

(methodical/defmethod injection/init-key ::handle [_ {:keys [router]} _]
  (http/ring-handler
   router
   (ring/create-default-handler)
   {:executor sieppari/executor}))

(def config
  {;; XXX: This MUST be overridden by the user for production use.
   ;; Each origin should be a regex of the domain, as in the commented out example below.
   ::cors-configuration
   {:origins [#_"https://tekacs.com"]}

   ::exception-handlers
   {}

   ::handlers
   {:context (injection/ref :factor/context)
    :sente-server (injection/ref :factor.sente.server/server)}

   ::routes
   {:handlers (injection/ref ::handlers)}

   ::router
   {:routes (injection/ref ::routes)
    :muuntaja-instance (injection/ref :factor.encoding/muuntaja-instance)
    :cors-configuration (injection/ref ::cors-configuration)
    :exception-handlers (injection/ref ::exception-handlers)}

   ::handle
   {:router (injection/ref ::router)}})
