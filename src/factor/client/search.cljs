(ns factor.client.search
  (:require ["fuse.js" :as Fuse]
            [cljs-bean.core :refer [bean ->js]]
            [factor.search :as search]))

(declare fuse-searcher)
(defrecord FuseSearcher [instance choices options]
  search/ChoiceSearcher
  (with-choices [_ choices] (fuse-searcher choices options))
  (with-options [_ options] (fuse-searcher choices options))
  (search [_ query] (map (fn [result] (bean result)) (.search instance query))))
(defn fuse-searcher [choices options]
  (->FuseSearcher (Fuse. (->js choices) (->js options)) choices options))
