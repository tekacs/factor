(ns factor.client.style
  (:require [com.tekacs.access :as a]
            [factor.dom :as dom]
            [factor.types :as ty :refer [=>]]))

(defn set-css-var! [key value & {:keys [sel] :or {sel ":root"}}]
  (let [elem (dom/query-selector sel)
        key (str "--" (name key))]
    (a/call! elem [:style :setProperty] key value)))

(defn css-url [url]
  (str "url(" url ")"))

(defn toggle-classes! [elem classes]
  (when elem
    (doseq [class classes]
      (a/call-in! elem [:classList :toggle] class))))

(defn viewport-height []
  (Math/max (a/get-in js/document [:documentElement :clientHeight]) (a/get js/window :innerHeight 0)))

(defn viewport-width []
  (Math/max (a/get-in js/document [:documentElement :clientWidth]) (a/get js/window :innerWidth 0)))

(defn scroll-bottom [elem]
  (+ (a/get elem :scrollTop) (a/get elem :scrollHeight)))

(defn can-hover? []
  (a/get (a/window! :matchMedia "(hover: hover)") :matches))

(def font-size$
  (delay
    (-> (a/get js/document :documentElement)
        (js/getComputedStyle)
        (a/get :fontSize)
        (js/parseFloat))))

(defn tailwind->px [tw]
  (/ (* @font-size$ tw) 4))

(ty/defn ^{:aave.core/enforce-purity false} match-media? [expression]
  (-> (a/window! :matchMedia expression) (a/get :matches)))

(ty/defn touchscreen?
  [] [=> boolean?]
  (match-media? "(hover: none) and (pointer: coarse)"))

(ty/defn standalone?
  [] [=> boolean?]
  (or (a/get-in js/window [:navigator :?standalone]) (match-media? "(display-mode: standalone)")))
