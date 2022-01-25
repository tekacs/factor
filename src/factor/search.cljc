(ns factor.search)

(defprotocol ChoiceSearcher
  (with-choices [this choices])
  (with-options [this options])
  (search [this query]))
