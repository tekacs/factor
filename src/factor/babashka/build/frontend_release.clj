#!/usr/bin/env bb
(ns factor.babashka.build.frontend-release
  (:require [babashka.process :refer [process check]]
            [factor.babashka.build.assemble :refer [commit-revision assemble!]]
            [factor.babashka.build.hash-assets :refer [hash-assets!]]))

(defn shadow-config
  []
  (let [revision      (commit-revision)
        version       (subs revision 0 7)
        optimizations (cond
                        ;; NOTE: Whitespace optimization doesn't substitute `:closure-defines`!
                        (some #{"--whitespace"} *command-line-args*) :whitespace
                        (some #{"--simple"} *command-line-args*)     :simple
                        :else                                        :advanced)]
    `{:closure-defines  {factor.client.config/REVISION ~revision
                         factor.client.config/VERSION  ~version}
      :compiler-options {:optimizations ~optimizations}}))

(defn release-js!
  []
  (check (process (conj '[npm run release -- --config-merge] (pr-str (shadow-config))) {:inherit true})))

(defn release-css!
  []
  (check (process '[npm run release:css] {:inherit true})))

(defn copy-capacitor!
  []
  (check (process '[npx cap copy] {:inherit true})))

;; TODO: Migrate this to tasks in bb.edn with babashka
(defn -main
  [& _]
  (let [build?     (some #{"--build"} *command-line-args*)
        build-js?  (or build? (some #{"--build-js"} *command-line-args*))
        build-css? (or build? (some #{"--build-css"} *command-line-args*))
        assemble?  (some #{"--assemble"} *command-line-args*)
        capacitor? (some #{"--capacitor"} *command-line-args*)
        all?       (not (or build-js? build-css? assemble? capacitor?))]
    (when (or all? build-js?)
      (release-js!))
    (when (or all? build-css?)
      (release-css!))
    (when (or all? assemble?)
      (assemble!)
      (hash-assets!))
    (when (or all? capacitor?)
      (copy-capacitor!))
    nil))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
