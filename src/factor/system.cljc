(ns factor.system
  (:require [aero.core :as aero]
            [clojure.string :as string]
            [factor.config :as config]
            [factor.types :as ty]
            [malli.core :as m]
            [integrant.core :as ig]))

(ty/def ::profile
  [:enum :development :test :production])

(defmethod ig/prep-key ::profile [_ profile]
  (or
   profile
   #?(:clj nil :cljs (some-> (aero/get-env "NODE_ENV") string/lower-case keyword))))

(defmethod ig/init-key ::profile [_ profile]
  (ty/assert-valid ::profile (str "Started with an invalid " ::profile) :dev))

(defn dev?
  [{::keys [profile] :as _system}]
  (= :development profile))

(defn test?
  [{::keys [profile] :as _system}]
  (= :test profile))

(defn prod?
  [{::keys [profile] :as _system}]
  (= :production profile))
