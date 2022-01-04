(ns factor.client.react
  {:client/npm-deps ["react" "react-dom"]}
  (:require ["react" :as react]
            ["react-dom" :as react-dom]
            [com.tekacs.access :as a]
            [factor.client.dom :as dom]
            [factor.client.types :as fct]
            [factor.debugging]
            [factor.types :as ty]
            [helix.children :as helix-children]
            [helix.core]
            [helix.experimental.refresh :as refresh]
            [helix.hooks :as hook]
            [integrant.core :as ig]
            [promesa.core :as pc]
            [shadow.lazy :as lazy])
  (:require-macros [factor.client.react :refer [$ defnc prop]]))

(declare $ render! unmount! refresh!)

(defmethod ig/init-key ::render [_ {:keys [target component]}]
  (let [root (dom/get-element-by-id target)]
    (render! root ($ component))
    root))

(defmethod ig/suspend-key! ::render [_ st]
  st)

(defmethod ig/resume-key ::render [_ _ _ st]
  ;; NOTE: This implementation does NOT respect changes to config -- it just does a hot reload.
  (refresh!)
  st)

(defmethod ig/halt-key! ::render [_ root]
  (unmount! root))

(ty/def ::element
  [:fn {:error/message "Should have a $$typeof field, which matches Symbol.for(\"react.element\")"}
   #(= (a/get % :$$typeof) (js/Symbol.for "react.element"))])

(ty/def ::child
  [:or string? ::element [:sequential [:ref ::child]] ifn?])

(defmulti props-spec (fn [type_] type_))
(defmulti props-explainer (fn [type_] type_))

(defn render! [root component]
  (a/call! react-dom :render component root))

(defn unmount! [root]
  (a/call! react-dom :unmountComponentAtNode root))

(defn refresh! []
  (let [log (a/get js/console :log)]
    (a/assoc! js/console :log (constantly nil))
    (refresh/refresh!)
    (a/assoc! js/console :log log)))

(defn lazy* [loadable]
  (react/lazy
    (fn []
      (-> (lazy/load loadable)
          (pc/then (fn [root-el]
                     ;; React.lazy expects to load a ES6 module with a React Component as default export

                     ;; this would be more correct in production settings
                     ;; #js {:default (r/reactify-component root-el)}

                     ;; we need wrap the loaded component one extra level so live-reload actually works
                     ;; since React will keep a reference to the initially loaded fn and won't update it
                     #js {:default (helix.core/factory @loadable)}))))))

(defn forward-ref [& args]
  (apply react/forwardRef args))

(defn function-child
  [props]
  (let [children (helix-children/children props)]
    (cond
      (fn? children) children
      (seq children) (some #(when (fn? %) %) children))))

(defn equality-cached
  [cache-atom$ value]
  (if-let [existing-instance (get @cache-atom$ value)]
    existing-instance
    (do
      (swap! cache-atom$ assoc value value)
      value)))

;; TODO: Switch to use-subscription
(defn use-atom [atom' & {:keys [cleanup]}]
  (let [[state set-state] (hook/use-state (when atom' @atom'))]
    (hook/use-effect
     [atom' set-state]
     (when atom'
       (let [key (-> :use-atom gensym name)]
         (add-watch atom' key #(set-state %4))
         #(do (remove-watch atom' key) (when cleanup (cleanup))))))
    state))