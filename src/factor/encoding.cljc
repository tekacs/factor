(ns factor.encoding
  "Support for encoding, based on Transit"
  (:require #?(:cljs [com.cognitect.transit.types :as transit-types])
            [cognitect.transit :as transit]
            [com.wsscode.pathom3.connect.operation.transit :as pcot]
            [factor.time :as time]
            [factor.types :as ty]
            #?(:clj [muuntaja.core :as muuntaja])
            [integrant.core :as ig]))

#?(:cljs
   ;; This is necessary to ensure that cljs.core/uuid? recognizes Transit UUIDs.
   (extend-type transit-types/UUID IUUID))

(ty/def ::codec
  [:map
   [:tag :string]
   [:type :any]
   [:write ifn?]
   [:read ifn?]])

(ty/def ::codecs
  [:sequential ::codec])

(defmethod ig/init-key ::fmt [_ _]
  "application/transit+json")

(defmethod ig/init-key ::write-handlers [_ {:keys [codecs]}]
  (merge
   (into {} (for [{:keys [tag type write]} codecs] [type (transit/write-handler (constantly tag) write)]))
   time/write-handlers
   pcot/write-handlers))

(defmethod ig/init-key ::read-handlers [_ {:keys [codecs]}]
  (merge
   (into {} (for [{:keys [tag read]} codecs] [tag (transit/read-handler read)]))
   time/read-handlers
   pcot/read-handlers))

(defmethod ig/init-key ::encoder-opts [_ {:keys [write-handlers]}]
  {:handlers write-handlers
   :transform transit/write-meta})

(defmethod ig/init-key ::decoder-opts [_ {:keys [read-handlers]}]
  {:handlers read-handlers})

#?(:clj
   (defmethod ig/init-key ::muuntaja-instance [_ {:keys [fmt encoder-opts decoder-opts]}]
     (muuntaja/create
      (-> muuntaja/default-options
          (assoc :default-format fmt)
          (update-in
           [:formats fmt]
           assoc
           :encoder-opts encoder-opts
           :decoder-opts decoder-opts)))))

(def config
  {::fmt {}
   ::write-handlers {}
   ::read-handlers {}
   ::encoder-opts {:write-handlers (ig/ref ::write-handlers)}
   ::decoder-opts {:read-handlers (ig/ref ::read-handlers)}
   #?@(:clj [::muuntaja-instance {:fmt (ig/ref ::fmt)
                                  :read-handlers (ig/ref ::read-handlers)
                                  :write-handlers (ig/ref ::write-handlers)}])})

(comment (ig/init config))
