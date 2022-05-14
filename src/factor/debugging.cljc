(ns factor.debugging
  (:require #?(:cljs [clojure.string :as string])
            #?(:cljs [factor.client.browser :as browser])
            [factor.types :as ty :refer [=>]]
            [fipp.edn :as fipp]))

(ty/defn fprint-str
  [value] [any? => string?]
  (with-out-str (fipp/pprint value)))

(ty/defn fprint
  [value] [any? => nil?]
  (println (fprint-str value)))

(defn log
  [& args]
  #?(:clj (fprint `[~@args])
     :cljs (apply js/console.log args)))

#?(:cljs
   (defn log-group
     [_level title & args]
     (let [stringify-if-not-chrome (if (= (browser/browser-name) :chrome) identity #(-> % str string/trim))]
       (apply js/console.groupCollapsed (map stringify-if-not-chrome title))
       (apply js/console.log (map stringify-if-not-chrome args))

       (js/console.groupCollapsed "trace")
       (js/console.trace)
       (js/console.groupEnd)

       (js/console.groupEnd))))
