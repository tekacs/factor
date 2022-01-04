(ns factor.environment
  (:require #?(:cljs [aero.core :as aero])
            #?(:cljs [clojure.string :as string])
            [factor.types :as ty]
            [integrant.core :as ig]))

(ty/def ::profile
  [:enum :development :test :production])

(defmethod ig/prep-key ::profile [_ profile]
  (or
   profile
   #?(:clj nil :cljs (some-> (aero/get-env "NODE_ENV") string/lower-case keyword))))

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
