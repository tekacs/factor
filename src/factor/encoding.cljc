(ns factor.encoding
  "Support for encoding, based on Transit"
  (:require [cognitect.transit :as transit]
            [com.wsscode.pathom3.connect.operation.transit :as pcot]
            [factor.time :as time]
            #?(:clj [muuntaja.core :as muuntaja])))

(def fmt "application/transit+json")

(def codecs
  [])

(def write-handlers
  (merge
   (into {} (for [[tag type write _] codecs]
              [type (transit/write-handler (constantly tag) write)]))
   time/write-handlers
   pcot/write-handlers))

(def read-handlers
  (merge
   (into {} (for [[tag _ _ read] codecs]
              [tag (transit/read-handler read)]))
   time/read-handlers
   pcot/read-handlers))

#?(:clj
   (def muuntaja-instance
     (muuntaja/create
      (-> muuntaja/default-options
          (assoc :default-format fmt)
          (update-in
           [:formats fmt]
           merge
           {:encoder-opts {:handlers write-handlers
                           :transform transit/write-meta}
            :decoder-opts {:handlers read-handlers}})))))
