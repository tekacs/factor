(ns factor.environment
  (:require #?(:cljs [clojure.string :as string])
            [factor.types :as ty]
            [integrant.core :as ig]))

(ty/def ::profile
  [:enum :development :test :production])

(defmethod ig/prep-key ::profile [_ profile]
  profile)

(defmethod ig/init-key ::profile [_ profile]
  (ty/assert-valid ::profile (str "Started with an invalid " ::profile) profile))

(defn dev?
  [{::keys [profile] :as _system}]
  (= :development profile))

(defn test?
  [{::keys [profile] :as _system}]
  (= :test profile))

(defn prod?
  [{::keys [profile] :as _system}]
  (= :production profile))
