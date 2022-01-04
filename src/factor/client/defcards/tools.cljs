(ns factor.client.defcards.tools
  (:require [clojure.string :as string]
            [factor.client.defcards.utils :as fcdu]
            [fipp.edn]
            [helix.core :refer [$ defnc]]
            [helix.hooks :as hook]
            [marked]))

(defn fprint-str [value]
  (with-out-str (fipp.edn/pprint value)))

;;region React internals

(defn !internal-instance [elem]
  (when-let [key (first (filter #(string/starts-with? % "__reactInternalInstance") (.keys js/Object elem)))]
    (aget elem key)))

(defn !closest-component [instance]
  (first (drop-while #(string? (.-type %)) (iterate #(.-return %) instance))))

(defn !find-root [^js/Object any-fiber]
  (let [root-fiber (last (take-while some? (iterate #(.-return %) any-fiber)))]
    (.-stateNode root-fiber)))

(defn !component-seq [root]
  (tree-seq (constantly true) (fn [v] (filter some? [(.-child ^js v) (.-sibling ^js v)])) root))

(defn !latest-instance [^js/Object instance]
  (let [root (.-current (!find-root instance))
        instance-id (.-_debugID instance)]
    (first (filter #(= (.-_debugID ^js/Object %) instance-id) (!component-seq root)))))

(defn !fiber->hook-states [^js/Object fiber]
  (let [fiber (!latest-instance fiber)]
    (map
      vector
      (.-_debugHookTypes fiber)
      (take-while some? (iterate #(.-next %) (.-memoizedState fiber))))))

(defn !display-hook [hook-type hook-value]
  (let [state (.-memoizedState hook-value)]
    (case hook-type
      "useRef" (let [fmt (fcdu/fprint-str (.-current state))] [fmt fmt])
      "useCallback" (let [[f deps] state] [(str "deps: " (fcdu/fprint-str deps)) (.toString f)])
      "useEffect" (.keys js/Object state)
      (let [fmt (fcdu/fprint-str state)] [fmt fmt]))))

(defnc HookView [{:keys [hook content]}]
  (let [[expanded? set-expanded] (hook/use-state false)
        [inline block] (!display-hook hook content)]
    ($ :li {:class "flex flex-col"}
       ($ :div {:class "w-full flex items-center truncate"}
          ($ :span {:class    "p-1 rounded text-blue-800 bg-blue-100 select-none cursor-pointer"
                    :on-click #(set-expanded not)} hook)
          ($ :span {:class "flex-grow ml-2"} inline))
       (when expanded?
         ($ :div {:class "w-full mt-2 ml-2 whitespace-pre font-mono"}
            block)))))

(defnc HookViewer [{:keys [instance render-atom]}]
  (let [[states set-states] (hook/use-state nil)
        render (fcdu/use-atom render-atom)
        update! #(set-states (-> instance !closest-component !fiber->hook-states))]
    (hook/use-effect [render] (update!))
    (if (empty? states)
      "No hooks"
      ($ :ul {:class "space-y-2"}
         (map-indexed (fn [idx [hook content]] ($ HookView {:key idx :hook hook :content content})) states)))))

(defnc ComponentHookTool [{:keys [wrapper-ref wrapper-renders]}]
  (when @wrapper-ref
    (let [wrapper-instance   (!internal-instance @wrapper-ref)
          component-instance (-> ^js wrapper-instance (.-child) (.-child))]
      ($ HookViewer {:instance component-instance :render-atom wrapper-renders}))))

;;endregion React internals

(defnc CodeTool [{:keys [card]}]
  ($ :span {:class "font-mono whitespace-pre-wrap"} (fprint-str (:form card))))

(defnc CardHookTool [{:keys [wrapper-ref wrapper-renders]}]
  (when @wrapper-ref
    ($ HookViewer {:instance (!internal-instance @wrapper-ref) :render-atom wrapper-renders})))

(defnc RenderCountTool [{:keys [wrapper-renders]}]
  (let [wrapper-count (fcdu/use-atom wrapper-renders)]
    ($ :ul
       ($ :li "Card Renders: " wrapper-count))))

(defnc HealthCheckTool [{:keys [wrapper-ref wrapper-renders]}]
  (fcdu/use-atom wrapper-renders)
  (when @wrapper-ref
    (if (-> @wrapper-ref !internal-instance !find-root)
      "Connected to a React root"
      "Disconnected from React root")))

(def tools
  (sorted-map
    :code
    {:name      :code
     :sigil     "</>"
     :component CodeTool}

    :hooks-card
    {:name      :hooks-card
     :sigil     "CH"
     :component CardHookTool}

    :hooks-component
    {:name      :hooks-component
     :sigil     "H$"
     :component ComponentHookTool}

    :render-count
    {:name      :render-count
     :sigil     "123"
     :component RenderCountTool}

    :health-check
    {:name      :health-check
     :sigil     "#"
     :component HealthCheckTool}))
