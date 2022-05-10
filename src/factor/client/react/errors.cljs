(ns factor.client.react.errors
  {:client/npm-deps ["react-error-boundary"]}
  (:require ["react-error-boundary" :refer [ErrorBoundary] :rename {ErrorBoundary REB}]
            [factor.client.react :refer [$ defnc prop] :as react]
            [factor.debugging :as debugging]))

(prop ErrorFallback [:map
                     [:error :any]
                     [:componentStack ::react/child]
                     [:resetErrorBoundary ifn?]])
(defnc ErrorFallback [{:keys [error componentStack resetErrorBoundary]}]
  (js/console.log error)
  ($ :div ["p-2 flex flex-col"]
     ($ :a {:on-click resetErrorBoundary} "Reset")
     ($ :br)
     ($ :div
        ($ :div ["font-mono whitespace-pre-wrap"] "message:\n" (ex-message error))
        ($ :br)
        ($ :div ["font-mono whitespace-pre-wrap"] "data:\n" (debugging/fprint-str (ex-data error)))
        ($ :br)
        ($ :div ["font-mono whitespace-pre-wrap"] "component stack:" componentStack))))

(prop ErrorBoundary)
(defnc ErrorBoundary [{:keys [children]}]
  ($ REB {:FallbackComponent ErrorFallback} children))
