#!/usr/bin/env bb
(ns factor.babashka.build.hash-assets
  (:require [babashka.fs :as fs]
            [clojure.string :as string])
  (:import (java.security MessageDigest)
           (java.io StringWriter)
           (java.nio.file Files LinkOption)))

(def hash-limit 32)
(def root-dir (fs/absolutize "."))
(def base-dir (fs/path root-dir "static"))
(def rewritten-files #".*\.(js|json|html|css|map|edn)$")

(defn file?
  [path]
  (Files/isRegularFile path (make-array LinkOption 0)))

(defn sha
  "From https://github.com/borkdude/babashka/blob/f15d609b45be2086e70756d04fb78fd1ea855d77/doc/examples.md#cryptographic-hash"
  [s limit]
  (let [hashed (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes s))
        sw     (StringWriter.)]
    (doseq [byte (take (/ limit 2) hashed)]
      (.append sw (format "%02X" byte)))
    (str sw)))

(defn file-mappings [kind]
  (let [kind-dir (fs/path base-dir kind)]
    (for [file (filter #(and (file? %) (not (re-find #"\..*\." (fs/file-name %)))) (fs/list-dir kind-dir))]
      (let [contents     (slurp (str file))
            hash         (sha contents hash-limit)
            new-filename (string/replace (fs/file-name file) #"\.(\w+$)" (format ".%s.$1" hash))
            new-file     (fs/path kind-dir new-filename)]
        [file new-file]))))

(defn mappings->mapper
  [mappings]
  (let [replacer-for (fn [[from to]] #(string/replace % (str (fs/relativize base-dir from)) (str (fs/relativize base-dir to))))]
    (apply comp (map replacer-for mappings))))

(defn replace-in-file! [file mapper]
  (let [filename (str file)]
    (when (re-matches rewritten-files filename)
      (println "=>" (str (fs/relativize base-dir filename)))
      (->> (slurp filename) (mapper) (spit filename)))))

(defn replace-everywhere! [mapper]
  (fs/walk-file-tree base-dir {:visit-file (fn [f _] (replace-in-file! f mapper) :continue)}))

(defn hash-assets!
  []
  (let [mappings (concat (file-mappings "style") (file-mappings "js"))]
    (println "Renaming files:")
    (doseq [[from to] mappings]
      (println (str (fs/relativize base-dir from)) "=>" (str (fs/relativize base-dir to))))
    (println)
    (println "Rewriting references:")
    (replace-everywhere! (mappings->mapper mappings))
    (doseq [[from to] mappings]
      (fs/move from to))))

(defn -main
  [& _]
  (hash-assets!))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
