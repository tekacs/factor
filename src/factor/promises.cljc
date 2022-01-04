(ns factor.promises
  "Support for promises based on Promesa"
  (:refer-clojure :exclude [resolve])
  (:require [clojure.core.async :as y]
            [factor.async :as async]
            [factor.types :as ty :refer [=>]]
            [helix.hooks :as hook]
            [promesa.core :as pc]))

#?(:cljs (ty/def ::thenable [:fn pc/thenable?]))
(ty/def ::promise [:fn pc/promise?])

(ty/defn ^{:aave.core/enforce-purity false} channel->promise
  [ch] [::async/readable => ::promise]
  (pc/create (fn [resolve _] (y/take! ch #(do (resolve %) (y/close! ch))))))

(ty/defn ^{:aave.core/enforce-purity false} to-channel!?
  [promise] [::promise => ::async/readable]
  (let [ch (y/promise-chan)]
    (-> promise
        (pc/then #(if (nil? %) (y/close! ch) (y/put! ch [% nil])))
        (pc/catch #(y/put! ch [nil %])))
    ch))

(defn promise->atom
  ([promise] (promise->atom promise identity nil))
  ([promise transform] (promise->atom promise transform nil))
  ([promise transform initial-value]
   (let [result-atom$ (atom initial-value)]
     (-> promise
         (pc/then #(reset! result-atom$ (transform %)))
         (pc/catch #(reset! result-atom$ %)))
     result-atom$)))

(ty/def :use-async/status [:enum :use-async/idle :use-async/pending :use-async/success :use-async/error])
(ty/def :use-async/value any?)
(ty/def :use-async/error any?)
(ty/def :use-async/start! ifn?)
(ty/def :use-async/result
  [:map
   :use-async/start!
   :use-async/status
   :use-async/value
   :use-async/error])
#?(:cljs
   (ty/defn ^{:aave.core/enforce-purity false} use-async
     "Based on https://usehooks.com/useAsync/"
     ([async-fn initial-value]
      [ifn? any? => :use-async/result]
      (use-async async-fn initial-value true))
     ([async-fn initial-value immediate?]
      [ifn? any? [:maybe boolean?] => :use-async/result]
      (let [[status set-status!] (hook/use-state :use-async/idle)
            [value set-value!]   (hook/use-state initial-value)
            [error set-error!]   (hook/use-state nil)
            start!
            (hook/use-callback [async-fn]
                               (fn []
                                 (set-status! :use-async/pending)
                                 (set-value! nil)
                                 (set-error! nil)

                                 (-> (async-fn)
                                     (pc/then (fn [res] (set-value! res) (set-status! :use-async/success)))
                                     (pc/catch (fn [err] (set-error! err) (set-status! :use-async/error))))))]
        (hook/use-effect [start! immediate?] (when immediate? (start!)))
        {:use-async/start! start!
         :use-async/status status
         :use-async/value  value
         :use-async/error  error
      ;; For backwards compatibility with uses in static.contentful, which can be rewritten later.
         :value            value}))))
