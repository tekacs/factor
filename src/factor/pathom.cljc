(ns factor.pathom
  (:require [factor.types :as ty]))

(ty/def ::eql (ty/from-spec-type :pathom/eql))
(ty/def ::ast (ty/from-spec-type :pathom/ast))
(ty/def ::entity (ty/from-spec-type :pathom/entity))
(ty/def ::lenient-mode? (ty/from-spec-type :pathom/lenient-mode?))

(ty/def ::channel :qualified-keyword)
(ty/def ::request [:or ::eql ::ast])

(ty/def ::request [:tuple ::channel ::request])
