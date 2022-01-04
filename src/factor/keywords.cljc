(ns factor.keywords
  (:require [factor.idents :as idents]
            [factor.types :as ty :refer [=>]]))

(ty/defn keyword->str
  "Turn a keyword into a string"
  [k] [[:maybe :keyword] => [:maybe :string]]
  (when k (subs (str k) 1)))

(comment (keyword->str :hello.world/how.you.doing) => "hello.world/how.you.doing")

(ty/defn str->keyword
  "Turn a string into a keyword (nil if an empty string)"
  [s] [[:maybe :string] => [:maybe :keyword]]
  (when (seq s) (keyword s)))

(comment (str->keyword "hello.world/how.you.doing") => :hello.world/how.you.doing)

(ty/defn namespace-under-keyword
  [lhs rhs] [keyword? [:or keyword? string?] => qualified-keyword?]
  (let [lhs (idents/ident->dotted-form lhs)
        rhs (cond-> rhs (keyword? rhs) idents/ident->dotted-form)]
    (keyword lhs rhs)))

(comment (namespace-under-keyword :parent.namespace/parent.name :child.namespace/child.name) => :parent.namespace.parent.name/child.namespace.child.name)

(ty/defn is-namespaced?
  "Is the keyword `kwd` (`:some.namespace/keyword`) namespaced under ns (`:some/namespace`)"
  [ns kwd] [keyword? keyword? => boolean?]
  (= (idents/ident->dotted-form ns) (namespace kwd)))

(comment (is-namespaced? :parent/namespace :parent.namespace/keyword) => true)

(ty/def ::unqualified-keyword
  [:and
   keyword?
   [:fn {:error/message "keyword should be unqualified"}
    #(nil? (namespace %))]
   [:fn {:error/message "keyword should have a non-empty name"}
    #(not-empty (name %))]])
