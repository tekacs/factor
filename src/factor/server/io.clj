(ns factor.server.io
  (:require [clojure.java.io :as io]
            [factor.types :as ty :refer [=>]]))

(ty/defn exists?
  [path] [:string => :boolean]
  (.exists (io/file path)))

(ty/defn is-file?
  [path] [:string => :boolean]
  (.isFile (io/file path)))

(ty/defn is-directory?
  [path] [:string => :boolean]
  (.isDirectory (io/file path)))

(ty/defn delete!
  [path] [:string => :any]
  (.delete (io/file path)))
