(ns factor.client.example
  (:require [hiccup.page :refer [html5]]))

(defn repl-http-handler
  "A fallback handler that serves an HTML page for not-found resources in dev mode.

  Set up in shadow-cljs with the configuration `{:dev-http {:handler *ns*/repl-http-handler}}`
  
  Based on https://github.com/thheller/shadow-cljs/blob/e6c7d36dc4a870c0b64286e0b590e69c06c63d43/src/main/shadow/cljs/devtools/server/web.clj#L117
  "
  [_]
  {:status 200
   :headers
   {"content-type" "text/html; charset=utf-8"
    ;; Don't cache this at all
    "cache-control" "max-age=0, no-cache, no-store, must-revalidate"
    "pragma" "no-cache"
    "expires" "0"}
   :body
   (html5
    {:lang "en"}
    [:head
     [:title "browser-repl"]
     [:script {:src "https://cdn.tailwindcss.com"}]]
    [:body {:class "font-mono"}
     [:div#root]
     [:script {:src "/js/factor.js"}]
     [:script "factor.client.example.main()"]])})
