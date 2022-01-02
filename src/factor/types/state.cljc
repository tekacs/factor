(ns factor.types.state
  "A basic atom-based registry for types in a namespace that is never reloaded on reset"
  #?(:clj (:require [clojure.tools.namespace.repl :as tnr])))

#?(:clj (tnr/disable-reload!))

(defonce registry$
  (atom {}))
