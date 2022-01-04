(ns factor.client.browser
  {:client/npm-deps ["detect-browser"]}
  (:require ["detect-browser" :as detect-browser]
            [cljs-bean.core :refer [bean]]))

(defn browser []
  (bean (detect-browser)))

(defn browser-name []
  (keyword (:name (browser))))
