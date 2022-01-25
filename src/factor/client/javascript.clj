(ns factor.client.javascript)

(defmacro *fn
  "Create a function using `fn` syntax that translates args from JS args."
  [& args]
  `(-> (fn ~@args)
       (factor.client.javascript/args->bean)))
