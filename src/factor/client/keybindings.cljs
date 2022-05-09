(ns factor.client.keybindings
  (:require ["mousetrap" :as Mousetrap]
            [com.tekacs.access :as a]
            [factor.client.dom :as dom]
            [factor.client.react :as react]
            [factor.collections :as collections]
            [factor.identifiers :as identifiers]
            [factor.lenses :as lenses]
            [factor.types :as ty]
            [helix.hooks :as hook]
            [lentes.core :as lentes]
            [taoensso.timbre :as timbre]))

(ty/def ::binding-root
  ;; {scope => root}
  [:map-of :string :string])
(react/defcontext use-binding-roots ::binding-root)

(defonce ^:private binding-roots-inner$
  (atom {}))

(ty/def ::binding-registry
  ;; {key, event => {root => {handler, condition-fn}}}
  [:map-of
   [:map
    [:key :string]
    [:event {:optional true} :string]]
   [:maybe
    [:map-of
     ;; root
     [:maybe :string]
     [:map
      [:handler ifn?]
      [:condition-fn [:maybe ifn?]]]]]])

(def ^:private binding-registry$
  (lentes/derive (lenses/validated-writes ::binding-registry) binding-roots-inner$))

(defn use-binding-scope
  "Return props and context, to be used and wrapped around some DOM to bind keys within that scope."
  [scope make-focusable?]
  (let [binding-roots (use-binding-roots)
        my-key        (hook/use-memo :once (str (identifiers/random-uuid)))]
    [;; Props, to be merged into a DOM element at the root of the binding tree.
     (hook/use-memo [my-key make-focusable?] (cond-> {:data-binding my-key} make-focusable? (assoc :tab-index 0)))
     ;; A React context function, to apply at the root of the binding tree.
     (hook/use-callback [scope binding-roots my-key] #(use-binding-roots (assoc binding-roots scope my-key) %))]))

(defn use-binding-scope-globally
  "Create a global binding root for a given scope, so that it activates no matter the element."
  [scope]
  (let [binding-roots (use-binding-roots)]
    (hook/use-callback [scope binding-roots] #(use-binding-roots (assoc binding-roots scope "global") %))))

(defn- global-handler-for
  [key]
  (fn [ev]
    (let [target                         (a/get ev :target)
          possible-roots                 (get @binding-registry$ key)
          enclosing-roots                (dom/all-enclosing-data target "binding")
          matching-binding               (some possible-roots enclosing-roots)
          ;; Always fall back to the :global handler if present.
          matching-binding               (or matching-binding (get possible-roots "global"))
          {:keys [handler condition-fn]} matching-binding]
      (when (or (not condition-fn) (condition-fn))
        (when handler
          (try
            (handler ev)
            (finally (a/call! ev :preventDefault))))))))

(defn- add-binding!
  [root key handler condition-fn]
  (when (get-in @binding-registry$ [key root])
    (throw (ex-info
            "Duplicate keybinding within a single root -- only one caller should manage a given [root, key] pair"
            {:root root :key key})))
  (let [{literal-key :key event :event} key]
    (swap! binding-registry$ assoc-in [key root] {:handler handler :condition-fn condition-fn})
    (Mousetrap/bind literal-key (global-handler-for key) (or event js/undefined))))

(defn- remove-binding!
  [root key]
  (swap! binding-registry$ update key dissoc root))

(defn- key->config
  [key]
  (cond
    (nil? key)    nil
    (string? key) {:key key}
    (map? key)    key))

;; NOTE: This exists to prevent this value from causing re-renders in the hook below.
(def ^:private constantly-true
  (constantly true))

(defn use-global-binding
  ([scope key handler] (use-global-binding scope key handler constantly-true))
  ([scope key handler condition]
   (let [binding-roots (use-binding-roots)
         root          (if (= scope :global) "global" (get binding-roots scope ::fail))]
     (if (= root ::fail)
       (timbre/warn (ex-info "Missing enclosing binding root for scope" {:scope scope :binding-roots binding-roots}))
       (hook/use-effect
        [root key handler condition]
        (when key (add-binding! root (key->config key) handler condition))
        #(when key (remove-binding! root (key->config key))))))))

(defn- wrapped-key-handler
  "Wrap an underlying key handler to prevent default actions."
  [handler condition]
  (fn [e]
    (when (condition)
      (try
        (handler e)
        (finally (a/call! e :preventDefault))))))

(defn use-binding
  ([elem key handler] (use-binding elem key handler (constantly true)))
  ([elem key handler condition]
   (hook/use-effect
    [elem key handler condition]
    (when elem
      (let [instance (Mousetrap elem)]
        (a/call! instance :bind key (wrapped-key-handler handler condition))
        #(a/call! instance :unbind key))))))

(defn use-keyboard-selection [items bind-root]
  (let [[selected set-selected] (hook/use-state nil)
        prev!                   (hook/use-callback [items set-selected] #(set-selected (fn [sel] (collections/last-before #{sel} items))))
        next!                   (hook/use-callback [items set-selected] #(set-selected (fn [sel] (collections/first-after #{sel} items))))
        clear!                  (hook/use-callback [set-selected] #(set-selected nil))]
    (use-binding bind-root "up" prev!)
    (use-binding bind-root "down" next!)
    (use-binding bind-root "escape" clear!)
    selected))
