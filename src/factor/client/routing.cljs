(ns factor.client.routing
  (:require-macros [factor.client.routing])
  (:require ["query-string" :as query-string]
            ["react-router-dom"
             :refer [BrowserRouter Link Switch Route useHistory useLocation useParams useRouteMatch generatePath]
             :rename {Link RLink, Switch RSwitch, Route RRoute, Redirect RRedirect}]
            [cljs-bean.core :refer [bean ->clj ->js]]
            [clojure.string :as string]
            [cognitect.transit :as transit]
            [com.tekacs.access :as a]
            #_[factor.client.components.alert :as alert]
            [factor.client.react :as react :refer [$ defnc prop]]
            [factor.client.types :as fct]
            [factor.types :refer [=>] :as ty]
            [goog.Uri :as uri]
            [malli.core :as m]))

(defonce transit-writer (transit/writer :json))
(defonce transit-reader (transit/reader :json))

(ty/def ::location
  [:map
   [:pathname string?]
   [:search {:optional true} string?]
   [:hash {:optional true} string?]
   [:state {:optional true} any?]])

(ty/def ::path-desc
  [:or
   string?
   ifn?
   ::location])

(ty/def ::route
  [:or
   [:tuple qualified-keyword?]
   [:tuple qualified-keyword? [:maybe [:map-of keyword? :any]]]
   [:tuple qualified-keyword? [:maybe [:map-of keyword? :any]] any?]])

(ty/def ::target
  [:or ::location ::route])

(ty/def ::path
  [:or string? [:vector string?]])

(ty/def ::path-match
  [:map
   [:path ::path]
   [:strict {:optional true} boolean?]
   [:exact {:optional true} boolean?]])

(ty/def ::params
  [:map-of keyword? [:maybe string?]])

(ty/def ::match
  [:maybe
   [:map
    [:params ::params]
    [:isExact boolean?]
    [:path string?]
    [:url string?]]])

(ty/def ::link-props
  [:map
   [:to ::target]
   [:replace {:optional true} boolean?]
   [:component {:optional true} ::react/element]])

(ty/def ::redirect-props
  [:map
   [:to ::target]
   [:push {:optional true} boolean?]
   [:from {:optional true} string?]
   [:exact {:optional true} boolean?]
   [:strict {:optional true} boolean?]
   [:sensitive {:optional true} boolean?]])

(ty/def ::history
  [:map
   [:get-action [:=> :cat [:enum :push :replace :pop]]]
   [:get-location [:=> :cat ::location]]
   [:push! [:=> [:cat :string :any] :any]]
   [:replace! [:=> [:cat :string :any] :any]]
   [:go! [:=> [:cat :int] :any]]
   [:back! [:=> :cat :any]]
   [:forward! [:=> :cat :any]]
   [:block! [:=> [:cat :string] :any]]])

(defmulti match-path (fn [route-id] route-id))

(defn path-for [[route-id params]]
  (generatePath (match-path route-id) (->js params)))

(defn absolute-url [path]
  (js/URL. path js/document.location))

(ty/defn norm
  [path?s] [[:or qualified-keyword? [:sequential qualified-keyword?]] => ::path]
  (condp m/validate path?s
    [:sequential qualified-keyword?] (mapv match-path path?s)
    qualified-keyword?               (match-path path?s)))

(defn normalize-location [location-or-route]
  (condp m/validate location-or-route
    map? (->js location-or-route)
    ::route (path-for location-or-route)
    ::fct/object location-or-route))

(ty/defn use-history
  [] [=> ::history]
  (let [history                                          (useHistory)
        {:keys [push replace go goBack goForward block]} (bean history)]
    {:get-action   #(-> history (a/get :action) string/lower-case keyword)
     :get-location #(-> history (a/get :location) ->clj)
     :push!        push
     :replace!     replace
     :go!          go
     :back!        goBack
     :forward!     goForward
     :block!       block}))

(ty/defn use-location
  [] [=> ::location]
  (let [{:keys [state] :as location} (->clj (useLocation))]
    (assoc location :state (when state (try (transit/read transit-reader state)
                                            (catch :default _ state))))))

(ty/defn use-state
  [] [=> any?]
  (:state (use-location)))

(ty/defn use-params
  [] [=> ::params]
  (bean (useParams)))

(ty/defn ^{:aave.core/enforce-purity false} use-query
  [] [=> ::params]
  (let [{:keys [search]} (use-location)]
    (bean (a/call! query-string :parse search))))

(ty/defn use-route-match
  [path] [[:or string? ::path-match] => ::match]
  (->clj (useRouteMatch (->js path))))

(prop Router)
(defnc Router [{:keys [children]}]
  (let [pathname (.. js/window -location -pathname)]
    ($ BrowserRouter
       ;; Use hash routing to ensure that iOS standalone mode doesn't show a toolbar on nav.
       ;; Using basename instead of HashRouter is a workaround to ensure state works.
       ;; https://github.com/ReactTraining/history/issues/435#issuecomment-302235708
       {:basename (when (fu/standalone?) (str pathname "#"))}
       children)))

(prop Link ::link-props)
(defnc Link [{:keys [className to] :as props}]
  ($ RLink [className] {:to (normalize-location to) :& (dissoc props :to)}))

(prop Redirect ::redirect-props)
(defnc Redirect [{:keys [to] :as props}]
  ($ RRedirect {:to (normalize-location to) :& (dissoc props :to)}))

(prop Switch [:map [:children some?]])
(defnc Switch [props]
  ($ RSwitch {:& props}))

(prop Route ::path-match)
(defnc Route [{:keys [path] :as props}]
  ($ RRoute {:path (->js path) :& (dissoc props :path)}))

(ty/defn location->path
  [location] [::location => :string]
  (let [{:keys [pathname search hash]} location
        search                         (if (= \? (first search)) (subs search 1) search)
        hash                           (if (= \# (first hash)) (subs hash 1) hash)]
    (cond-> pathname (seq search) (str "?" search) (seq hash) (str "#" hash))))

(defn navigate!
  ([history route] (navigate! history route {}))
  ([history route {:keys [replace?]}]
   (let [[_ _ state] route
         state       (transit/write transit-writer state)
         path        (cond
                       (string? route) route
                       (vector? route) (path-for route))]
     (if replace?
       ((:replace! history) path state)
       ((:push! history) path state)))))

(defn home!
  ([history] (home! history {}))
  ([history opts] (navigate! history [:app/home] opts)))

(defn back! [{:keys [back!] :as _history}]
  (back!))

(defn our-origin?
  [uri]
  (let [uri (if (string? uri) (uri/parse uri) uri)]
    (or (get fconfig/our-domains (.getDomain uri))
        (.hasSameDomainAs ^js uri (uri/parse js/document.location)))))

(defn navigate-to-internal-href!
  [history href]
  (let [{:keys [push!]} history
        uri             (uri/parse href)]
    (if (our-origin? uri)
      (-> {:pathname (.getPath ^js uri)
           :search   (.getQuery ^js uri)
           :hash     (.getFragment ^js uri)}
          (location->path)
          (push!))
      (push! href))))

(defn navigate-to-external-href!
  [href]
  (a/assoc-in! js/window [:location :href] href))
