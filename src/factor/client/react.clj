(ns factor.client.react
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [malli.core :as m]
            [malli.error :as me]))

;; TODO: Consider a reader tag that spits out a more distinguished data structure.
(defn- process-args [args]
  (reduce
    (fn [acc arg]
      (cond
        (vector? arg) (assoc acc :classes arg)
        (map? arg) (assoc acc :props arg)
        :else (reduced acc)))
    nil
    args))

(defn- named-roundtrip [n]
  (cond
    (keyword? n) [(namespace n) (name n) keyword]
    (symbol? n) [(namespace n) (name n) symbol]
    (string? n) ["" n str]
    :else (throw (ex-info "Not a keyword, symbol or string." {:obj n}))))

(defn- process-class [cls]
  (cond
    (keyword? cls) (name cls)
    (and (coll? cls) (= 'quote (first cls))) (mapv str (flatten (second cls)))
    :else cls))

(defn- runtime-join-classes [classes]
  `(->> ~classes flatten (string/join " ")))

(defn- compact-map [map]
  (into {} (for [[k v] map] (if (some? v) [k v] nil))))

;; The features that this $ macro adds are:
;; - A vector of classes...
;; - ... that joins all sorts of different things (strings, keywords, nested collections, nils)
;; TODO: I want to switch to factory functions + helix.dom, so evaluate whether the newer builtin support suffices.
;; It doesn't look like the native support handles nested vectors: https://github.com/lilactown/helix/pull/47/files
;; I should probably redo the defnc function to receive and transform the factory function, to normalize :class myself
(defmacro $ [tag & args]
  (let [{:keys [classes props]} (process-args (take 3 args))
        body (remove (set/union #{classes} #{props}) args)
        [tag-ns tag-name constructor] (named-roundtrip tag)
        tag (constructor tag-ns tag-name)
        props (and props (compact-map (dissoc props :class)))
        classes (mapv process-class classes)
        runtime-classes (runtime-join-classes classes)
        props (if (empty? classes) props (assoc props :className runtime-classes))
        ;; Workaround until https://github.com/Lokeh/helix/commit/45c9cdf4c93ba81f178be43c6bc69ffcd3c7b505
        props (if (contains? props :&)
                (assoc (dissoc props :&) '& (:& props))
                props)]
    `(helix.core/$ ~tag ~props ~@body)))

(defmacro <> [& children]
  `(helix.core/<> ~@children))

(defmacro $! [tag & args]
  `($ ~tag ~@args))

(s/def ::body
  (s/cat
    :head (s/alt :doc (s/cat :doc string? :props vector?)
                 :undoc (s/cat :props vector?))
    :opts (s/? map?)
    :body (s/* any?)))

(defn- split-body [body]
  (let [{:keys [head opts body]} (s/conform ::body body)
        {:keys [doc props]} (val head)]
    {:doc   doc
     :props props
     :opts  opts
     :body  body}))

(defn bind-props [props]
  (let [[{:keys [as] :as props-arg}] props
        is-map? (map? props-arg)]
    (cond
      (and is-map? as) [props-arg as]
      is-map? [(assoc props-arg :as 'props) 'props]
      (nil? props-arg) ['_ '_]
      :else [props-arg props-arg])))

;; TODO: Check props with clojure.spec
;; TODO: Do something with docstrings at runtime... only in dev?
(defmacro
  ^{:arglists '([name doc-string? [params*] body])}
  defnc [type & body]
  (let [default-opts                  {:helix/features {:fast-refresh              true
                                                        :metadata-optimizations    true
                                                        :check-invalid-hooks-usage true}}
        full-name                     (symbol (str *ns*) (name type))
        {:keys [doc props opts body]} (split-body body)
        [props-arg props-name]        (bind-props props)
        maybe-ref                     (filter some? [(second props)])]
    `(helix.core/defnc ~type [~props-arg ~@maybe-ref]
       ~(merge default-opts opts)
       (if-let [props-failure#
                (when-let [explainer# (factor.client.react/props-explainer '~full-name)]
                  (explainer# ~props-name))]
         (throw
           (ex-info
             (str "Invalid props passed to "
                  '~full-name
                  ": "
                  (try (me/humanize props-failure#) (catch :default _# (str "\n" (factor.debugging/fprint-str props-failure#)))))
             {:component '~full-name
              :failure   props-failure#})))
       ~@body)))

(defmacro prop
  ([type_] `(prop ~type_ [:map]))
  ([type_ props-spec]
   `(do
      (defmethod factor.client.react/props-spec
        '~(symbol (str *ns*) (name type_))
        [_#]
        ~props-spec)
      (defmethod factor.client.react/props-explainer
        '~(symbol (str *ns*) (name type_))
        [_#]
        (m/explainer ~props-spec)))))

(defmacro cond$ [className props children & pairs]
  (let [cond-pairs#
        (apply concat
               (for [[condition# component#] (partition 2 pairs)]
                 `[~condition# ($ ~component# [~className] ~props ~@children)]))]
    `(cond ~@cond-pairs#)))

(defmacro lazy [component-sym]
  `(factor.client.react/lazy* (shadow.lazy/loadable ~component-sym)))
