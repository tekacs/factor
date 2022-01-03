(ns factor.config
  (:refer-clojure :exclude [read-string])
  (:require [aero.core :as aero]
            [integrant.core :as ig]
            [malli.core :as m]
            #?(:cljs [cljs.tools.reader.reader-types :refer [source-logging-push-back-reader]])))

(defmethod aero/reader 'ig/ref
  [_ _ value]
  (ig/ref value))

(defmethod aero/reader 'ig/refset
  [_ _ value]
  (ig/refset value))

(defn read-string
  [edn-string]
  (->> edn-string
       (#?(:clj java.io.StringReader.
           :cljs source-logging-push-back-reader))
       aero/read-config))
