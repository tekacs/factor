(ns factor.system.state
  {:dev/once true
   :clojure.tools.namespace.repl/load false})

#?(:clj
   (def preparer nil)
   :cljs
   (def preparer$ (atom nil)))

#?(:clj
   (def config nil)
   :cljs
   (def config$ (atom nil)))

#?(:clj
   (def system nil)
   :cljs
   (def system$ (atom nil)))
