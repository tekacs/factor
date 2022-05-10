(ns factor.types
  "Type safety via Malli, including a registry in types.state -- require :as ty"
  {:require-as 'ty}
  (:refer-clojure :exclude [defn defn-])
  #?(:cljs (:require-macros [factor.types]))
  (:require [aave.code]
            [aave.core :as av]
            [clojure.spec.alpha :as spec]
            [clojure.test.check.generators :as gen]
            [factor.types.state :refer [registry$]]
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

(clojure.core/defn from-spec-type
  [spec-type]
  [:fn
   {:error/fn (fn [{:keys [value]} _] (spec/explain-str spec-type value))}
   #(spec/valid? spec-type %)])

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

(register! ::instance
           (m/-simple-schema
            (fn [_ [class]]
              {:type            ::instance
               :type-properties {:error/message (str "Should be an instance of " (pr-str class))}
               :pred            #(instance? class %)
               :min             1
               :max             1})))

(register! ::derived-from
           (m/-simple-schema
            (fn [_ [parent]]
              {:type            ::derived-from
               :type-properties {:error/message (str "Should be derived from " (pr-str parent))}
               :pred            #(isa? % parent)
               :min             1
               :max             1})))

(register! ::atom [:fn {:error/message "should be an atom"}
                   #?(:clj (partial instance? clojure.lang.Atom)
                      :cljs #(satisfies? cljs.core/IAtom %))])
