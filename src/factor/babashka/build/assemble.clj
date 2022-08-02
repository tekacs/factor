#!/usr/bin/env bb
(ns factor.babashka.build.assemble
  (:require [babashka.fs :as fs]
            [babashka.process :refer [$ check]]
            [clojure.string :as string]))

(def codepush-bucket
  "https://SOME-CODEPUSH-BUCKET.s3.us-east-2.amazonaws.com/")

(def root-dir
  (fs/absolutize "."))

(def assembly-target
  (fs/path root-dir "static"))

;; Format of both of these maps:
;; {[src-dir src-glob] target-dir}

(def files
  {["public" "index.html"]           ""
   ["public/style" "*.css"]          "style/"
   ["public/font" "*.woff*"]         "font/"
   ["dist" "*.{js,edn,json}"]        "js/"
   })

(def dirs
  {})

(defn copies
  [copy-map]
  (for [[[src-dir src-glob] target-dir] copy-map
        src-file                        (fs/glob (fs/path root-dir src-dir) src-glob)
        :let                            [filename (fs/file-name src-file)
                                         target-dir (fs/path assembly-target target-dir filename)]]
    [src-file target-dir]))

(defn- print-copy!
  [prefix from to]
  (binding [*out* *err*]
    (println
      prefix
      "|"
      (str (fs/relativize root-dir from))
      "=>"
      (str (fs/relativize root-dir to)))))

(defn clean!
  []
  (when (fs/directory? assembly-target)
    (fs/delete-tree assembly-target)))

(defn copy-assets!
  []
  (doseq [[from to] (copies files)]
    (print-copy! "f" from to)
    (fs/create-dirs (fs/parent to))
    (fs/copy from to))
  (doseq [[from to] (copies dirs)]
    (print-copy! "d" from to)
    (fs/create-dirs to)
    (fs/create-dirs (fs/parent to))
    (fs/copy-tree from to {:replace-existing true})))

(defn commit-revision
  []
  (if-let [env (System/getenv "BUDDY_EXECUTION_REVISION")]
    env
    (-> ^{:out :string} ($ git rev-parse HEAD) check :out string/trim)))

(defn manifest
  []
  (let [revision (commit-revision)]
    {:versions {:web revision :mobile revision}
     :assets   {revision (str codepush-bucket revision ".zip")}}))

(defn write-manifest!
  []
  (println "f | => manifest.edn")
  (fs/create-dirs assembly-target)
  (->> (manifest) pr-str (spit (str (fs/path assembly-target "manifest.edn"))))
  nil)

(defn assemble!
  []
  (clean!)
  (copy-assets!)
  (write-manifest!))

(defn -main
  [& _]
  (assemble!))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
