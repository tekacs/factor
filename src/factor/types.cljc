(ns factor.types
  "Type safety via Malli, including a registry in types.state -- require :as ty"
  {:require-as 'ty}
  (:refer-clojure :exclude [defn defn-])
  #?(:cljs (:require-macros [factor.types]))
  (:require #?(:cljs ["randexp" :as randexp])
            #?(:cljs ["random-seed" :as random-seed])
            #?(:cljs [com.tekacs.access :as a])
            [aave.code]
            [aave.core :as av]
            [clojure.test.check.generators :as gen]
            [factor.types.state :refer [registry$]]
            [lambdaisland.regal :as regal]
            [lambdaisland.regal.generator :as regal-gen]
            [malli.core :as m]
            [malli.error :as me]
            [malli.generator :as mg]
            [malli.impl.util :as miu]
            [malli.registry :as mr]
            [malli.util :as mu])
  #?(:clj (:import (clojure.lang IAtom))))

(def => :ret)

(clojure.core/defn assert-valid
  [type error-message value]
  (if (m/validate type value)
    value
    (let [explain (m/explain type value)]
      (throw (ex-info error-message {:type type :value value :explain explain :humanize (me/humanize explain)})))))

(clojure.core/defn local-registry
  [registry]
  (mr/composite-registry
    registry
    (m/default-schemas)))

(clojure.core/defn exception-safe-fn
  [function fallback-fn]
  (fn [arg]
    (try (function arg)
         (catch #?(:clj Exception :cljs :default) _ (fallback-fn arg)))))

(clojure.core/defn spec-for [ident]
  (get @registry$ ident))

(clojure.core/defn register! [ident spec]
  (swap! registry$ assoc ident spec))

(clojure.core/defn register-all! [spec-map]
  (swap! registry$ merge spec-map))

(clojure.core/defn mapped-type
  ([type mapper] (mapped-type type mapper nil nil nil))
  ([type mapper type-properties properties-schema children-schema]
   (reify m/IntoSchema
     (-type [this] type)
     (-type-properties [this] type-properties)
     (-properties-schema [this options] properties-schema)
     (-children-schema [this options] children-schema)
     (-into-schema [this properties children options]
       (let [underlying-type (m/schema (mapper properties children options))]
         (reify m/Schema
           (-validator [this] (m/-validator underlying-type))
           (-explainer [this path] (m/-explainer underlying-type path))
           (-parser [this] (m/-parser underlying-type))
           (-unparser [this] (m/-unparser underlying-type))
           (-transformer [this transformer method options]
             (m/-transformer underlying-type transformer method options))
           (-walk [this walker path options]
             (when (m/-accept walker this path options)
               (m/-outer walker this path (vec children) options)))
           (-properties [this] properties)
           (-options [this] options)
           (-children [this] children)
           (-parent [this] (mapped-type type mapper))
           (-form [this] (m/-create-form type properties children))))))))

(clojure.core/defn safe-mapper
  [f]
  (fn [& args]
    (try
      (apply f args)
      (catch #?(:clj Exception, :cljs js/Error) _ ::invalid))))

(clojure.core/defn transform-type
  [type map-fn gen-map-fn underlying-type-fn]
  (reify m/IntoSchema
    (-type [this] type)
    (-type-properties [this])
    (-properties-schema [this options])
    (-children-schema [type options])
    (-into-schema [this properties children options]
      (let [underlying-type (m/schema (underlying-type-fn properties children options))
            mapper          (safe-mapper (map-fn properties children options))
            gen-mapper      (safe-mapper (gen-map-fn properties children options))]
        (reify m/Schema
          (-validator [this]
            (let [underlying-validator (m/-validator underlying-type)]
              (fn [value]
                (let [mapped (mapper value)]
                  (when-not (= mapped ::invalid) (underlying-validator mapped))))))
          (-explainer [this path]
            (let [underlying-explainer (m/-explainer underlying-type path)]
              (fn explain [x in acc]
                (let [mapped (mapper x)]
                  (if (= mapped ::invalid)
                    (conj acc (miu/->SchemaError path in this x ::mapper-failed "mapper failed"))
                    (underlying-explainer mapped in acc))))))
          (-parser [this] (comp (m/-parser underlying-type) mapper))
          (-unparser [this] (comp gen-mapper (m/-unparser underlying-type)))
          (-transformer [this transformer method options]
            (comp (m/-transformer underlying-type transformer method options) mapper))
          (-walk [this walker path options]
            (when (m/-accept walker this path options)
              (m/-outer walker this path (vec children) options)))
          (-properties [this] (assoc properties
                                     :gen/gen (gen/fmap gen-mapper (mg/generator underlying-type))))
          (-options [this] options)
          (-children [this] children)
          (-parent [this] (transform-type type mapper gen-mapper underlying-type))
          (-form [this] (m/-create-form type properties children)))))))

(clojure.core/defn fspec
  "This is a placeholder for a real fspec function once it's added by malli"
  [& {:keys [args ret] :as spec}]
  (when-let [failure (m/explain [:map [:args [:vector any?]] [:ret any?]] spec)]
    (println failure)
    (throw (ex-info "Must provide both :args and :ret to fspec" failure)))
  [:fn {:error/message (str "should be a function taking " args " and returning " ret)} fn?])

(clojure.core/defn fspec!
  "THIS CALLS THE FUNCTION UNDER VALIDATION!"
  [& {:keys [args ret]}]
  (let [generator (mg/generator (into [:tuple] args))
        validator (m/validator ret)
        explainer (m/explainer ret)]
    (m/-simple-schema
     {:type            [args ret]
      :pred            #(->> (mg/generate generator) (apply %) (validator))
      :type-properties {:error/fn (fn [{:keys [value]} _] (->> (mg/generate generator) (apply value) (explainer)))}})))

#?(:cljs
   (clojure.core/defn randexp-seeded [regex]
     (fn [seed]
       (let [underlying (randexp. regex)]
         (a/assoc! underlying :randInt (a/get (random-seed. seed) :intBetween))
         underlying))))

(clojure.core/defn regex-gen [regex]
  #?(:clj (mg/generator [:re regex])
     :cljs (gen/fmap
            (comp
             #(.gen ^js %)
             (randexp-seeded regex)) (gen/choose js/Number.MIN_SAFE_INTEGER js/Number.MAX_SAFE_INTEGER))))

(clojure.core/defn re
  ([regex] (re {} regex))
  ([props regex] [:re (merge {:gen/gen (regex-gen regex)} props) regex]))

(clojure.core/defn regal
  ([regal-expr] (regal {} regal-expr))
  ([{:keys [bind?] :or {bind? true} :as props} regal-expr]
   (let [regal-expr (if bind? [:cat :start regal-expr :end] regal-expr)]
     [:re
      (merge {:regal regal-expr :gen/gen (regal-gen/gen regal-expr)} props)
      (regal/regex regal-expr)])))

(register! ::atom-of (transform-type
                      ::atom-of
                      (constantly deref)
                      (constantly atom)
                      (fn [_ children _] (first children))))

(register! ::instance (m/-simple-schema
                       (fn [_ [class]]
                         {:type            ::instance
                          :type-properties {:error/message (str "Should be an instance of " (pr-str class))}
                          :pred            #(instance? class %)
                          :min             1
                          :max             1})))

(register! ::derived-from (m/-simple-schema
                           (fn [_ [parent]]
                             {:type            ::derived-from
                              :type-properties {:error/message (str "Should be derived from " (pr-str parent))}
                              :pred            #(isa? % parent)
                              :min             1
                              :max             1})))

;; Unfortunately there's no real way to check this without a registry on hand.
(register! ::schema [:fn {:error/message "should be a malli schema"}
                     any?])

(register! ::registry [:fn {:error/message "should be a malli registry"}
                       (comp some? mr/registry)])

(register! ::fn [:or
                 [:fn {:error/message "should be a function"} fn?]
                 keyword?
                 set?])

(register! ::atom [:fn {:error/message "should be an atom"}
                   #?(:clj (partial instance? clojure.lang.Atom)
                      :cljs #(satisfies? cljs.core/IAtom %))])

(register! ::uppercase (into [:enum] "ABCDEFHIJKLMNOPQRSTUVWXYZ"))

(register! ::regex [:fn {:error/message "should be a regex"}
                    #?(:clj (partial instance? java.util.regex.Pattern)
                       :cljs regexp?)])

(register! ::bytes #?(:clj bytes? :cljs string?))

(register! ::unqualified-keyword [:and
                                  keyword?
                                  [:fn {:error/message "keyword should be unqualified"}
                                   #(nil? (namespace %))]
                                  [:fn {:error/message "keyword should have a non-empty name"}
                                   #(not-empty (name %))]])

(register! ::exception [::instance #?(:clj Throwable :cljs cljs.core/ExceptionInfo)])

(def registry
  (mr/composite-registry
   (m/default-schemas) ; This can be reduced for DCE in future if desired.
   (mu/schemas)
   (mr/mutable-registry registry$)
   (mr/dynamic-registry)))

(mr/set-default-registry! registry)

;; def is mangled below this line

(clojure.core/defn def
  [ident spec]
  (register! ident spec)
  spec)

(defmacro defn
  {:arglists '([name doc-string? attr-map? [params*] [schemas*] ? body])}
  [& args]
  `(av/>defn ~@args))

(defmacro defn-
  {:arglists '([name doc-string? attr-map? [params*] [schemas*] ? body])}
  [& args]
  `(av/>defn- ~@args))
