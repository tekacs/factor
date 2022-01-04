(ns factor.client.defcards
  (:require ["marked" :as marked]
            ["react-error-boundary" :refer [ErrorBoundary]]
            ["react-router" :refer [generatePath]]
            ["react-router-dom"
             :refer [Link Switch Route BrowserRouter useParams]
             :rename {BrowserRouter Router}]
            [cljs-bean.core :refer [bean]]
            [clojure.set :as set]
            [com.tekacs.access :as a]
            [factor.client.defcards.tools :as fcdt]
            [factor.client.defcards.utils :as fcdu]
            [helix.core :refer [$ defnc]]
            [helix.hooks :as hook])
  (:require-macros [factor.client.defcards]))

(defn routing [basepath]
  {::home basepath
   ::ns   (str basepath "/ns/:ns")})

(def card
  [:map
   ;; These are set by the defcard macro
   [:key symbol?]
   [:name string?]
   [:line [:maybe int?]]
   [:form any?]
   [:component any?]

   ;; This is internal
   [:gone {:optional true} boolean?]

   ;; These can be merged in by the user
   [:doc {:optional true} string?]
   [:collapsed? {:optional true} boolean?]
   [:class {:optional true} string?]
   [:inputs {:optional true} [:map-of keyword? any?]]])

(defmulti config (fn [key] key))

;; TODO: Implement restart behaviour.
(defn restart! [_before _after])

(defnc CardHeader [{:keys [card collapsed? active-tools dispatch]}]
  ($ :div {:class "p-2 bg-gray-100 hover:bg-gray-200 flex flex-row justify-between select-none cursor-pointer"}
     ($ :div {:class "pl-1 flex-grow" :on-click #(dispatch [::toggle-collapse])}
        (:name card))
     ($ :div {:class "space-x-1"}
        (for [[mode _] fcdt/tools]
          ($ :button {:key      mode
                      :class    (str "w-10 text-center select-none cursor-pointer rounded "
                                     (when (contains? active-tools mode) "bg-gray-500 text-white"))
                      :on-click #(do (dispatch [::toggle-tool mode]) (a/call! % :stopPropagation))}
             (:sigil (fcdt/tools mode)))))))

(defnc BarLabel [{:keys [children]}]
  ($ :span {:class "w-14 p-2 text-center inline-block bg-gray-100 select-none"} children))

(defnc DocBar [{:keys [doc]}]
  (when doc
    ($ :div {:class "divide-x border-t"}
       ($ BarLabel "doc")
       ($ :div {:class "p-2 inline-block" :dangerouslySetInnerHTML #js {:__html (marked doc)}}))))

(defnc InputBar [{:keys [card input set-input!]}]
  (when-let [inputs (when-let [inputs (:inputs card)] (keys inputs))]
    ($ :div {:class "divide-x border-t"}
       ($ BarLabel "input")
       (for [i inputs]
         ($ :span {:key      i
                   :class    (str "p-2 inline-block select-none cursor-pointer " (when (= i input) "bg-gray-300"))
                   :on-click #(set-input! i)}
            (name i))))))

(defnc CardTools [{:keys [card wrapper-ref wrapper-renders active-tools]}]
  ($ :div {:class (str "flex flex-col divide-y divide-gray-400 border-b border-gray-400"
                       (when (not-empty active-tools) "border-t"))}
     (for [[mode _] fcdt/tools]
       (when (contains? active-tools mode)
         ($ :div {:key mode :class "px-2 py-2"}
            ($ (:component (fcdt/tools mode))
               {:card            card
                :wrapper-ref     wrapper-ref
                :wrapper-renders wrapper-renders}))))))

(defmulti card-reducer (fn [_ [action _]] action))

(defmethod card-reducer ::toggle-collapse [state _]
  (update state :collapsed? not))

(defmethod card-reducer ::toggle-tool [state [_ tool]]
  (update
    state
    :active-tools
    (fn [tools]
      (if (contains? tools tool)
        (set/difference tools #{tool})
        (set/union tools #{tool})))))

(defnc ComponentErrorFallback [{:keys [error componentStack resetErrorBoundary]}]
  (js/console.log error)
  ($ :div
     ($ :div {:class "border-b"}
        ($ BarLabel "error")
        ($ :span {:class "p-2"} "This card threw an error during rendering."))
     ($ :div {:class "p-2" :role "alert"}
        ($ :a {:class    "inline-block p-2 border cursor-pointer select-none"
               :on-click resetErrorBoundary}
           "Reset")
        ($ :br)
        ($ :pre (a/get error :message))
        ($ :br)
        ($ :pre "data:\n" (fcdt/fprint-str (ex-data error)))
        ($ :br)
        ($ :pre "component stack:" componentStack))))

(defnc ComponentWrapper [{:keys [class component input wrapper-ref wrapper-renders]}]
  (swap! wrapper-renders inc)
  (let [render (if input ((component) input) (component))]
    (when render
      ($ ErrorBoundary
         {:FallbackComponent ComponentErrorFallback}
         ($ :div {:ref wrapper-ref :class (str "relative p-2 " class)}
            render)))))

(defnc CardBody [{:keys [card state]}]
  (let [component          (:component card)
        inputs             (:inputs card)
        [input set-input!] (hook/use-state (when inputs (-> inputs first key)))
        component-ref      (hook/use-ref nil)
        wrapper-renders    (hook/use-ref (atom 0))]
    ($ :div
       ($ DocBar {:doc (:doc card)})
       ($ InputBar {:card card :input input :set-input! set-input!})
       ($ CardTools
          {:card            card
           :wrapper-ref     component-ref
           :wrapper-renders @wrapper-renders
           :active-tools    (:active-tools state)})
       ($ ComponentWrapper
          {:class           (:class card)
           :component       component
           :input           (get inputs input)
           :wrapper-ref     component-ref
           :wrapper-renders @wrapper-renders}))))

(defnc Card [{:keys [card]}]
  (let [[state dispatch] (hook/use-reducer
                           card-reducer
                           {:active-tools #{}
                            :collapsed?   (:collapsed? card)})
        collapsed?       (:collapsed? state)
        active-tools     (:active-tools state)]
    ($ :div {:class "border rounded"}
       ($ CardHeader {:card         card
                      :collapsed?   collapsed?
                      :active-tools active-tools
                      :dispatch     dispatch})
       (when-not collapsed?
         ($ CardBody {:card card :state state})))))

(def &link-header "py-2 px-3 bg-gray-200 select-none cursor-pointer")

(defnc CardNamespace [{:keys [routes]}]
  (let [[_ refresh-ns!] (hook/use-state 0)
        refresh-ns!     (hook/use-callback :once #(refresh-ns! inc))
        {:keys [ns]}    (bean (useParams))
        card-keys       (filterv #(= (namespace %) ns) (keys (methods config)))
        cards           (mapv config card-keys)]
    (fcdu/use-global-binding "ctrl+c" refresh-ns!)
    ($ :div {:class "p-2 flex flex-col gap-y-2"}
       ($ :div {:class "flex"}
          ($ Link {:className (str "flex-grow " &link-header) :to (generatePath (::home routes))}
             ($ :span {:class "font-medium"} ns))
          ($ :div {:class "w-2"})
          ($ :span {:class &link-header :on-click refresh-ns!} "refresh namespace (C-c)"))
       (for [card (->> cards (remove :gone) (sort-by :line))]
         ($ Card {:key (:name card) :card card})))))

(defnc NamespacePicker [{:keys [routes]}]
  ($ :div {:class "flex flex-col pt-2 px-2 gap-y-2"}
     (for [ns (->> (methods config) keys (map namespace) distinct sort)]
       ($ :div {:key ns :class [&link-header "hover:bg-gray-300"]}
          ($ Link {:className "font-medium" :to (generatePath (::ns routes) #js {:ns ns})} ns)))))

(defnc CardRoot [_]
  (let [routes (routing "/internal/defcards")]
    ($ Router
       ($ Switch
          ($ Route {:path (::ns routes)} ($ CardNamespace {:routes routes}))
          ($ Route {:path (::home routes)} ($ NamespacePicker {:routes routes}))))))
