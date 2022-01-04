(ns factor.client.defcards.utils
  (:require ["mousetrap" :as Mousetrap]
            [com.tekacs.access :as a]
            [fipp.edn :rename {pprint fprint}]
            [helix.hooks :as hook]))

(defn use-atom [atom' & {:keys [cleanup]}]
  (let [[state set-state] (hook/use-state nil)]
    (hook/use-effect [atom' set-state]
                     (set-state @atom')
                     (let [key (-> :use-atom gensym name)]
                       (add-watch atom' key #(set-state %4))
                       #(do (remove-watch atom' key) (when cleanup (cleanup)))))
    state))

(defn fprint-str [value]
  (with-out-str (fprint value)))

(defn- wrapped-key-handler
  "Wrap an underlying key handler to prevent default actions."
  [handler condition]
  (fn [e]
    (when (condition)
      (try
        (handler e)
        (finally (a/call! e :preventDefault))))))

(defn use-global-binding
  ([key handler] (use-global-binding key handler (constantly true)))
  ([key handler condition]
   (hook/use-effect [key handler condition]
                    (when key (Mousetrap/bind key (wrapped-key-handler handler condition)))
                    #(when key (Mousetrap/unbind key)))))

(defn use-binding
  ([elem key handler] (use-binding elem key handler (constantly true)))
  ([elem key handler condition]
   (hook/use-effect [elem key handler condition]
                    (when elem
                      (let [instance (Mousetrap elem)]
                        (a/call! instance :bind key (wrapped-key-handler handler condition))
                        #(a/call! instance :unbind key))))))
