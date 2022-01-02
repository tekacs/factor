(ns factor.async
  "Support for asynchronicity, based on core.async"
  (:require [clojure.core.async :as y]
            #?(:clj [clojure.core.async.impl.protocols]
               :cljs [cljs.core.async.impl.protocols])
            [factor.types :as ty]
            [promesa.core :as pc]))

;; TODO: Effect handling here could be well-approached using a >defn-go macro:
;; https://github.com/gnl/ghostwheel/issues/24#issuecomment-511208880

(ty/def ::readable
  [:fn #(satisfies?
          #?(:clj  clojure.core.async.impl.protocols/ReadPort
             :cljs cljs.core.async.impl.protocols/ReadPort)
          %)])
(ty/def ::writeable
  [:fn #(satisfies?
          #?(:clj  clojure.core.async.impl.protocols/WritePort
             :cljs cljs.core.async.impl.protocols/WritePort)
          %)])
(ty/def ::channel
  [:fn #(satisfies?
          #?(:clj  clojure.core.async.impl.protocols/Channel
             :cljs cljs.core.async.impl.protocols/Channel)
          %)])
(ty/def ::handler
  [:fn #(satisfies?
          #?(:clj  clojure.core.async.impl.protocols/Handler
             :cljs cljs.core.async.impl.protocols/Handler)
          %)])
(ty/def ::buffer
  [:fn #(satisfies?
          #?(:clj  clojure.core.async.impl.protocols/Buffer
             :cljs cljs.core.async.impl.protocols/Buffer)
          %)])

(ty/def ::promise
  #?(:clj [:fn #(satisfies? java.util.concurrent.CompletableFuture)]
     :cljs [:schema :foundation.promises/promise]))

(defn reaction-loop
  ([ch handler] (reaction-loop ch handler {}))
  ([ch handler {:keys [close?]}]
   (let [!control (y/promise-chan)
         !src     ch]
     (y/go-loop []
       (let [[msg _] (y/alts! [!control !src])]
         (if-not (nil? msg)
           (do
             ;; FIXME: Use manifold deferreds instead of thread and mix in core.async for Datomic use.
             #?(:clj  (y/thread (handler msg))
                :cljs (handler msg))
             (recur))
           (when close? (y/close! ch)))))
     !control)))

(defn to-atom
  ([ch] (to-atom ch identity))
  ([ch map-fn] (to-atom ch map-fn (atom nil)))
  ([ch map-fn at]
   (y/go-loop []
     (when-let [next-value (y/<! ch)]
       (reset! at (map-fn next-value))
       (recur)))
   at))

(defn terminate!?
  "Handle both result and error cases of a promise and stop handling here. Returns nil."
  [p% f err-f]
  (pc/handle
    p%
    (fn [good bad]
      (cond
        good (f good)
        bad  (err-f bad))))
  nil)
