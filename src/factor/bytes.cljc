(ns factor.bytes
  #?(:clj (:import java.nio.charset.Charset))
  (:require [factor.types :as ty]))

(ty/def ::bytes
  #?(:clj bytes? :cljs string?))

#?(:clj
   (def ^:private ^Charset utf-8
     (Charset/forName "UTF-8")))

(defn str->bytes
  "Convert from string to bytes -- prefer a serializer like Nippy when serializing!"
  #?(:clj [^String s] :cljs [s])
  #?(:clj (when s (.getBytes s utf-8))
     :cljs s))

(defn bytes->str
  "Convert from bytes to string"
  #?(:clj [^bytes b] :cljs [b])
  #?(:clj (when b (String. b utf-8)) :cljs b))
