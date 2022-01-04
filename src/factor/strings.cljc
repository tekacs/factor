(ns factor.strings
  (:require [clojure.string :as string]))

(defn space-join
  "Join &items with spaces, joining any inline collections without spaces."
  [& items]
  (string/join " " (map #(apply str %) items)))

(defn capitalize-first
  "Capitalize only the first character of the entire string, s"
  [s]
  (str (-> s first string/upper-case) (subs s 1)))

(defn title-case
  [s]
  (as-> s $ (string/split $ #" ") (map string/capitalize $) (string/join " " $)))
