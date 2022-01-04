(ns factor.errors
  "Error handling support -- require :as err"
  {:require-as 'err}
  #?(:cljs (:refer-clojure :exclude [isa?])
     :clj (:refer-clojure :exclude [isa?]))
  (:require [factor.identifiers :as identifiers]
            [factor.types :refer [=>] :as ty]
            [malli.core :as m]
            [taoensso.timbre :as timbre]))

(ty/def :error/id :uuid)
(ty/def :error/type [::ty/derived-from ::error])
(ty/def :error/public? :boolean)
(ty/def :error/message :string)
(ty/def :error/client-facing-id :any)

(ty/def ::error
  [:map
   :error/id
   :error/type
   [:error/public? {:optional true}]
   [:error/message {:optional true}]
   [:error/client-facing-id {:optional true}]

   [::ty/actual {:optional true} :any]
   [::ty/expected {:optional true} :any]
   [::ty/explanation {:optional true} [:or :map :string]]
   [::ty/humanized {:optional true} :string]])

(ty/def ::exception
  [::instance #?(:clj Throwable :cljs js/Error)])

(ty/defn error?
  "Is this value an error?"
  [value] [:any => :boolean]
  (m/validate ::error value))

(ty/defn isa?
  "Is this error of an :error/type derived from the named `parent-error-type`?"
  [value parent-error-type] [:map :error/type => :boolean]
  (clojure.core/isa? (:error/type value) parent-error-type))

(ty/defn expect
  "... read the function definition to understand how this one works"
  [error-types expected-fn fallback-fn]
  [[:set :error/type] ifn? ifn? => ifn?]
  (fn [errors]
    (let [[expected remaining] ((juxt filter remove) #(error-types (:error/type %)) errors)]
      (when (and expected-fn (seq expected)) (expected-fn expected))
      (when (and fallback-fn (seq remaining)) (fallback-fn remaining)))))

(defn log-exception
  "Given an ID and an Exception, log the Exception with the ID added to its ex-data and return the ID"
  [id ex]
  (let [explanation   (:malli/explanation (ex-data ex))
        repackaged-ex (if (ex-data ex)
                        (ex-info (ex-message ex)
                                 (cond-> (ex-data ex)
                                   explanation (assoc :malli/explanation "Message above")
                                   :else       (assoc :error/client-facing-id id))
                                 (ex-cause ex))
                        ex)]
    (timbre/error repackaged-ex id explanation))
  id)

(ty/defn secret-error
  "Create an error map that will never reach the client"
  [error-type ex] [:error/type ::exception => ::error]
  {:error/id (log-exception (identifiers/random-uuid) ex)
   :error/type error-type})

(ty/defn public-error
  "Create an error map for an error that will be passed through to the client.
  
  Public data is stored in `:error/data`, as error maps must only have :error/* keys."
  [error-type message data] [:error/type [:maybe :string] [:maybe :map] => ::error]
  {:error/public? true
   :error/id (identifiers/random-uuid)
   :error/type error-type
   :error/message message
   :error/data data})

(ty/defn error->exception
  "Turn an error map into a throwable exception."
  ([error] [::error => ::exception] (error->exception error nil))
  ([error cause]
   [::error [:maybe ::exception] => ::exception]
   (if (some? cause)
     (ex-info "" error cause)
     (ex-info "" error))))

;; Authentication errors, used internally by server.*
(derive ::auth-error ::error)

;; Errors on the part of the server, unavoidable by the client
(derive ::server-error ::error)

;; Errors on the part of the client, fixable by the client
(derive ::client-error ::error)
