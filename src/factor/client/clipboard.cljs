(ns factor.client.clipboard)

(defn copy%!
  [text]
  (.. js/navigator -clipboard (writeText text)))

(defn text%
  []
  (.. js/navigator -clipboard (readText)))
