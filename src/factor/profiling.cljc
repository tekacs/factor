(ns factor.profiling)

#?(:clj
   (defn set-profiling-level!
     [level]
     (System/setProperty "taoensso.tufte.min-level" (str level))
     (require '[taoensso.tufte] :reload-all)))
