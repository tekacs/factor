(ns factor.client.preload
  {:client/npm-deps ["react-refresh"]}
  (:require [helix.experimental.refresh :as r]))

(r/inject-hook!)
