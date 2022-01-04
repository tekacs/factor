(ns factor.client.dom
  (:require [com.tekacs.access :as a]))

(defn get-element-by-id [id]
  (a/document! :getElementById id))
