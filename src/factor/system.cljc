(ns factor.system
  (:require #?(:clj [clojure.tools.namespace.repl :as tnr])
            [factor.system.state :as state]
            [factor.types :as ty]
            [integrant.core :as ig]
            [lentes.core :as lentes]
            [malli.core :as m]
            [malli.error :as me]))

;; TODO: Use this to validate, as is done in integrant. Not done yet because it requires overriding `ig/init` and `ig/resume`, which I'm not sure I want to do.
;; Taken from https://github.com/weavejester/integrant/blob/32a46f5dca8a6b563a6dddf88bec887be3201b08/src/integrant/core.cljc#L405
(defmulti pre-init-spec @#'ig/normalize-key)
(defmulti post-init-spec @#'ig/normalize-key)

(defn- spec-exception [system k v spec ed]
  (ex-info (str "Spec failed on key " k " when building system\n"
                (me/humanize ed))
           {:reason   ::build-failed-spec
            :system   system
            :key      k
            :value    v
            :spec     spec
            :explain  ed}))

(defn- assert-pre-init-spec [system key value]
  (when-let [spec (pre-init-spec key)]
    (when-not (m/validate spec value)
      (throw (spec-exception system key value spec (m/explain spec value))))))

(declare reset)

#?(:cljs
   (defn ^:dev/after-load shadow-reload! []
     (reset)))

#?(:clj (tnr/disable-reload! (find-ns 'integrant.core)))

(def <>preparer
  (lentes/lens
   (fn [_st] #?(:clj state/preparer :cljs @state/preparer$))
   (fn [_st update-fn]
     #?(:clj (alter-var-root #'state/preparer update-fn)
        :cljs (swap! state/preparer$ update-fn)))))

(def <>config
  (lentes/lens
   (fn [_st] #?(:clj state/config :cljs @state/config$))
   (fn [_st update-fn]
     #?(:clj (alter-var-root #'state/config update-fn)
        :cljs (swap! state/config$ update-fn)))))

(def <>system
  (lentes/lens
   (fn [_st] #?(:clj state/system :cljs @state/system$))
   (fn [_st update-fn]
     #?(:clj (alter-var-root #'state/system update-fn)
        :cljs (swap! state/system$ update-fn)))))

(defn set-config-loader!
  "Set the function used to load config whenever the system is started."
  [config-loader-fn]
  (lentes/put <>preparer config-loader-fn nil))

(defn set-prep!
  "Set the function used to load config whenever the system is started.
  
  The function is automatically composed with `integrant.core/prep` to get the config ready for use."
  [config-loader-fn]
  (set-config-loader! (comp ig/prep config-loader-fn)))

(defn- prep-error []
  (ex-info "No system preparer function found." {}))

(defn prep
  "Prepare the system to be loaded, by running the preparer (config loader) function to populate config."
  []
  (if-let [prep (lentes/focus <>preparer nil)]
    (do (lentes/put <>config (prep) nil) :prepped)
    (throw (prep-error))))

(defn- halt-system
  "Utility to shut down the system when it exists."
  [system]
  (when system (ig/halt! system)))

(defn- build-system
  "Utility to build a new system safely, handling exceptions during building by halting the budding system."
  [build wrap-ex]
  (try
    (build)
    (catch #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo) ex
      (when-let [system (:system (ex-data ex))]
        (try
          (ig/halt! system)
          (catch #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo) halt-ex
            (throw (wrap-ex ex halt-ex)))))
      (throw ex))))

(defn- init-system
  "Initialize the system (or a subset of keys), providing an exception wrapper for failures to boot + halt."
  [config keys]
  (build-system
   (if keys
     #(ig/init config keys)
     #(ig/init config))
   #(ex-info "Config failed to init; also failed to halt failed system"
             {:init-exception %1}
             %2)))

(defn- resume-system
  "Resume the system (or a subset of keys), providing an exception wrapper for failures to boot + halt."
  [config system]
  (build-system
   #(ig/resume config system)
   #(ex-info "Config failed to resume; also failed to halt failed system"
             {:resume-exception %1}
             %2)))

(defn init
  "Halt any running system and then init a new system (or subset, given keys)."
  ([] (init nil))
  ([keys]
   (let [cfg (lentes/focus <>config nil)]
     (lentes/over <>system
                  (fn [sys]
                    (halt-system sys)
                    (init-system cfg keys))
                  nil))
   :initiated))

(defn go
  "Load config (using (prep)) and then init a new system."
  ([] (go nil))
  ([keys]
   (prep)
   (init keys)))

(defn clear []
  (lentes/over <>system (fn [sys] (halt-system sys) nil) nil)
  (lentes/put <>config nil nil)
  :cleared)

(defn halt []
  (lentes/over <>system (fn [sys] (halt-system sys) nil) nil)
  :halted)

(defn suspend []
  (when-let [sys (lentes/focus <>system nil)]
    (ig/suspend! sys))
  :suspended)

(defn resume []
  (if-let [prep (lentes/focus <>preparer nil)]
    (let [cfg (prep)]
      (lentes/put <>config cfg nil)
      (lentes/over <>system (fn [sys]
                              (if sys
                                (resume-system cfg sys)
                                (init-system cfg nil))) nil)
      :resumed)
    (throw (prep-error))))

(defn reset []
  (suspend)
  #?(:clj (tnr/refresh :after 'factor.system/resume)
     :cljs (resume)))

(defn reset-all []
  (suspend)
  #?(:clj (tnr/refresh-all :after 'factor.system/resume)
     :cljs (resume)))

(defn hard-reset []
  (halt)
  #?(:clj (do (tnr/clear) (tnr/refresh-all :after 'factor.system/go))
     :cljs (go)))

(defn load-namespaces []
  (prep)
  #?(:clj (ig/load-namespaces (lentes/focus <>config nil))))
