(ns factor.regex
  "Regex support, generators and types -- in CLJS, requires randexp and random-seed from NPM"
  {:client/npm-deps ["randexp" "random-seed"]}
  (:require #?(:cljs ["randexp" :as randexp])
            #?(:cljs ["random-seed" :as random-seed])
            #?(:cljs [com.tekacs.access :as a])
            #?(:cljs [clojure.test.check.generators :as gen])
            [factor.types :as ty]
            [lambdaisland.regal :as regal]
            [lambdaisland.regal.generator :as regal-gen]
            #?(:clj [malli.generator :as mg])))

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

(ty/def ::regex
  [:fn {:error/message "should be a regex"}
   #?(:clj (partial instance? java.util.regex.Pattern)
      :cljs regexp?)])
