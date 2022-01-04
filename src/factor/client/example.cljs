(ns factor.client.example
  (:require [factor.client.dom :as dom]
            [factor.client.react :as react]))

;; Note the configuration in shadow-cljs.edn
;; Notably, using `factor.client.preload` in `[the-build :devtools :preloads]` drives `react-refresh` for hot reloading.

(react/prop ExampleComponent)
(react/defnc ExampleComponent [_]
  (react/$ :div ["p-2"] "This is an example"))

(defn ^:export main []
  (react/render
   (dom/get-element-by-id "root")
   (react/$ ExampleComponent)))
