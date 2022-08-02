(ns factor.babashka.build.init
  (:require [babashka.fs :as fs]
            [babashka.tasks :refer [clojure shell]]
            [clojure.string :as str]
            [factor.babashka.build.assemble :refer [commit-revision]]))

(defn fetch-safe
  [m]
  (fn [k]
    (if-let [v (k m)]
      v
      (throw (ex-info "Tried to read an invalid key" {:key k})))))

(defn pass-args [& xs]
  (concat xs *command-line-args*))

(def paths
  (let [root (str (fs/canonicalize "."))
        home (System/getProperty "user.home")]
    (fetch-safe
      {;; TODO: Walk up the tree until we find `bb.edn` as the root.
       :root    root
       :home    home
       :m2/root (str (fs/path root "scripts" "m2-settings.xml"))
       :m2/home (str (fs/path home ".m2" "settings.xml"))
       :datomic (str (fs/path home "misc/datomic-pro"))})))

(def config
  (fetch-safe
    {:shadow/release
     (fn shadow-release-config [optimizations]
       ;; NOTE: Whitespace optimization doesn't substitute `:closure-defines`!
       (when-not (#{:whitespace :simple :advanced} optimizations)
         (throw (ex-info "Optimizations must be one of :whitespace, :simple, :advanced" {:value optimizations})))
       (let [revision (commit-revision)
             version  (subs revision 0 7)]
         {:compiler-options {:optimizations optimizations}
          :closure-defines  {'factor.client.config/REVISION revision
                             'factor.client.config/VERSION  version}}))

     :server/dev
     '{:deps
       {nrepl/nrepl        {:mvn/version "0.9.0"}
        cider/cider-nrepl  {:mvn/version "0.28.5"}}
       :aliases
       {:cider/nrepl
        {:main-opts
         ["-m" "nrepl.cmdline"
          "--middleware" "[cider.nrepl/cider-middleware]"]}}}}))

(def shell-config
  (fetch-safe
    {:node/dev  {:extra-env {"NODE_ENV" "development"}}
     :node/prod {:extra-env {"NODE_ENV" "production"}}

     :datomic {:dir (paths :datomic)}}))

(def aliases
  (let [client-dev     "-M:client:client-dev:server:server-dev"
        client-release "-M:client:client-prod"
        server-dev     "-M:server:server-dev:server-test"
        server-prod    "-M:server:server-prod"]
    (fetch-safe
      {:server/dev      server-dev
       :server.dev/repl (str server-dev ":cider/nrepl")
       :server/release  server-prod
       :server/uberjar  (str server-prod ":depstar")

       :client/dev     client-dev
       :client/release client-release

       :shadow/dev     (str client-dev ":shadow")
       :shadow/release (str client-release ":shadow")})))

(def arguments
  (let [server-kaocha-default [(str (aliases :server/release) ":server-test") "-m" "kaocha.runner"]
        shadow-release-opt    (fn [optimizations]
                                [(aliases :shadow/release)
                                 "release" "default"
                                 "--verbose"
                                 "--config-merge" (pr-str ((config :shadow/release) optimizations))])]
    (fetch-safe
      {:server/deps           ["-P" (aliases :server/release)]
       :server.dev/repl       ["-J-XX:-OmitStackTraceInFastThrow"
                               "-Sdeps" (pr-str (config :server/dev))
                               (aliases :server.dev/repl)]
       :server/release        [(aliases :server/release)]
       :server.kaocha/default server-kaocha-default
       :server.kaocha/verbose (into server-kaocha-default ["--reporter" "kaocha.report/documentation"])
       :server/pom            ["-Spom"]
       :server/uberjar        [(aliases :server/uberjar)
                               "-m" "hf.depstar.uberjar"
                               "target/app.jar"
                               "-C" "-m" "setup"]

       :client.deps/clj ["-P" (aliases :shadow/release)]
       :client/dev      [(aliases :client/dev)]
       :client/release  [(aliases :client/release)]

       :shadow.dev/build          [(aliases :shadow/dev) "compile" "default" "--verbose"]
       :shadow.dev/watch          [(aliases :shadow/dev) "watch" "default" "--verbose"]
       :shadow.release/whitespace (shadow-release-opt :whitespace)
       :shadow.release/simple     (shadow-release-opt :simple)
       :shadow.release/advanced   (shadow-release-opt :advanced)
       :shadow/build-report       [(aliases :shadow/release) "run" "shadow.cljs/build-report" "default" "report.html"]

       :chrome.dev/watch [(aliases :shadow/dev) "watch" "chrome" "--verbose"]})))

(def commands
  (let [postcss ["./node_modules/.bin/postcss" "client/css" "--base" "client/css" "--dir" "public/style"]]
    (fetch-safe
      {:echo            (pass-args "echo")
       :client.deps/npm ["npm" "install"]
       :datomic         [(shell-config :datomic) "bin/transactor" "config/transactor.properties"]
       :postcss/watch   (vec (concat [(shell-config :node/dev)] postcss ["-w"]))
       :postcss/build   (into [(shell-config :node/prod)] postcss)
       :capacitor/copy  ["npx" "cap" "copy"]})))

(defn run! [commands-key]
  (let [args       (commands commands-key)
        config-arg (first args)
        config-arg (when (map? config-arg) config-arg)
        config     (merge {:dir (paths :root)} config-arg)
        args       (flatten (if config-arg (rest args) args))]
    (print (str "$ " (str/join " " args) " % " (pr-str config) "\n"))
    (flush)
    (apply shell config args)))

(defn clj-run! [arguments-key]
  (let [args (arguments arguments-key)
        args (flatten ["-J-Dmalli.registry/type=custom" "-J-XX:+UseG1GC" args])]
    (print (str "$ clojure " (str/join " " args) "\n"))
    (flush)
    (apply clojure args)))
