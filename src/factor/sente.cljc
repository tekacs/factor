(ns factor.sente
  (:require [factor.encoding :as encoding]
            [factor.types :as ty]
            [integrant.core :as ig]
            [taoensso.sente.packers.transit :refer [->TransitPacker]]))

(ty/def ::event [:tuple :qualified-keyword :any])

(defmethod ig/init-key ::packer [_ {:keys [encoder-opts decoder-opts]}]
  (->TransitPacker :json encoder-opts decoder-opts))

(def config
  {::packer {:encoder-opts (ig/ref ::encoding/encoder-opts)
             :decoder-opts (ig/ref ::encoding/decoder-opts)}})
