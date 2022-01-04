(ns factor.atoms
  (:require [factor.types :as ty]))

(ty/def ::atom-of
  (ty/transform-type
   ::atom-of
   (constantly deref)
   (constantly atom)
   (fn [_ children _] (first children))))

(ty/def ::atom
  [:fn {:error/message "should be an atom"}
   #?(:clj (partial instance? clojure.lang.Atom)
      :cljs #(satisfies? cljs.core/IAtom %))])
