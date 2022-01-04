(ns factor.client.types
  (:refer-clojure :exclude [js-obj])
  (:require [cljs-bean.core :refer [bean ->js]]
            [com.tekacs.access :as a]
            [factor.types :as ty]))

(ty/def ::object
  [:and
   some?
   [:fn {:error/message "typeof should be \"object\""} #(= (js* "typeof ~{}" %) "object")]
   [:fn {:error/message "prototype should be Object"}
    #(= (js/Object.getPrototypeOf %) (a/get js/Object :prototype))]])

(ty/def ::js-arr
  (ty/transform-type
   ::js-arr
   (constantly seq)
   (constantly into-array)
   (fn [props children _] (into [:sequential props] children))))

(ty/def ::js-obj
  (ty/transform-type
   ::object
   (constantly bean)
   (constantly ->js)
   (fn [props children _] (into [:map props] children))))
