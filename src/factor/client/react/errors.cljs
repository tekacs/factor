(ns factor.client.react.errors
  {:npm-deps["react-error-boundary"]}
  (:require ["react-error-boundary" :refer [ErrorBoundary] :rename {ErrorBoundary REB}]
            [factor.debugging :as debugging]
            [factor.errors :as errors]
            [factor.client.react :refer [$ defnc prop]]))

(prop ErrorFallback [:map
                     [:error ::errors/error]
                     [:componentStack ::child]
                     [:resetErrorBoundary ifn?]])
(defnc ErrorFallback [{:keys [error componentStack resetErrorBoundary]}]
  (js/console.log error)
  ($ :div ["p-2-safe flex flex-col"]
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
