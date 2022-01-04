(ns factor.client.defcards)

(defn- process-args
  [args]
  (case (count args)
    2 [(first args) nil (last args)]
    3 args
    (throw (ex-info "Wrote number of args passed to defcard" {:count (count args)}))))

(defmacro
  ^{:arglists '([card-name component] [card-name config component])}
  defcard
  [& args]
  (let [[card-name config component] (process-args args)
        ns#                          (str *ns*)
        local-name#                  (name card-name)
        symbol#                      (symbol ns# local-name#)
        config#                      {:key       `'~symbol#
                                      :name      local-name#
                                      :line      (:line (meta &form))
                                      :form      `'~component
                                      :component `(fn [] ~component)}]
    `(when js/goog.DEBUG
       (let [final-config# (merge ~config# ~config)]
         (assert (malli.core/validate factor.client.defcards/card final-config#))
         (defmethod factor.client.defcards/config '~symbol# [_#] (merge ~config# ~config))))))
