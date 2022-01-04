(ns factor.regex
  "Regex support, generators and types -- in CLJS, requires randexp and random-seed from NPM"
  {:client/npm-deps ["randexp" "random-seed"]}
  (:require #?(:cljs ["randexp" :as randexp])
            #?(:cljs ["random-seed" :as random-seed])
            #?(:cljs [com.tekacs.access :as a])
            #?(:cljs [clojure.string :as string])
            #?(:cljs [clojure.test.check.generators :as gen])
            [factor.types :as ty :refer [=>]]
            [lambdaisland.regal :as regal]
            [lambdaisland.regal.generator :as regal-gen]
            #?(:clj [malli.generator :as mg])))

#?(:cljs
   (ty/defn re-escape
     "Escape characters in a string for the platform regex engine.
     
     From https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions#Escaping"
     [s] [:string => :string]
     ; $& means the whole matched string
     (string/replace s #"[.*+\-?^${}()|\[\]\\]" "\\$&")))

;; Based on https://stackoverflow.com/a/18737013
#?(:cljs
   (ty/defn ^{:aave.core/enforce-purity false} re-pos
     [re s] [::ty/regex :string => [:vector [:cat :int :int [:+ :string]]]]
     (let [re (js/RegExp. (a/get re :source) "g")]
       (loop [res []]
         (if-let [m (a/call! re :exec s)]
           (let [idx  (a/get m :index)
                 text (first m)]
             (recur (conj res (vec (concat [idx (+ idx (count text))] m)))))
           res)))))

#?(:cljs
   (defn randexp-seeded [regex]
     (fn [seed]
       (let [underlying (randexp. regex)]
         (a/assoc! underlying :randInt (a/get (random-seed. seed) :intBetween))
         underlying))))

(defn regex-gen [regex]
  #?(:clj (mg/generator [:re regex])
     :cljs (gen/fmap
            (comp
             #(.gen ^js %)
             (randexp-seeded regex)) (gen/choose js/Number.MIN_SAFE_INTEGER js/Number.MAX_SAFE_INTEGER))))

(defn re
  ([regex] (re {} regex))
  ([props regex] [:re (merge {:gen/gen (regex-gen regex)} props) regex]))

(defn regal
  ([regal-expr] (regal {} regal-expr))
  ([{:keys [bind?] :or {bind? true} :as props} regal-expr]
   (let [regal-expr (if bind? [:cat :start regal-expr :end] regal-expr)]
     [:re
      (merge {:regal regal-expr :gen/gen (regal-gen/gen regal-expr)} props)
      (regal/regex regal-expr)])))

(ty/def ::regex
  [:fn {:error/message "should be a regex"}
   #?(:clj (partial instance? java.util.regex.Pattern)
      :cljs regexp?)])
