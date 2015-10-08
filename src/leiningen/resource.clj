 (ns leiningen.resource
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [stencil.loader]
            [stencil.core :as stencil]
            [leiningen.compile :as lcompile]
            [leiningen.clean :as lclean]
            [bultitude.core :as bultitude]
            [robert.hooke :as hooke]))

;; Borrowed from https://github.com/emezeske/lein-cljsbuild/blob/master/plugin/src/leiningen/cljsbuild.clj

(def ^:private lein2?
  (try
    (require 'leiningen.core.main)
    true
    (catch java.io.FileNotFoundException _
      false)))

(defn dest-from-src
  "Change a src file to a dest file by replacing the directory.  

  Returns the destination file name as a `File`"
  [src-path dest-path ^java.io.File f]
  (let [s (count src-path)
        src (.getPath f)
        fnamex (subs src  s)
        fname (if (#{\/} (first fnamex)) (subs fnamex 1) fnamex)]
    (io/file dest-path fname)))

(defn msg 
  ([{:keys [silent] :as project-info}  text]
     (when-not silent
       (println  text))
     text)
  ([{:keys [silent] :as project-info} label text]
     (when-not silent
       (println label text))
     text)
  ([{:keys [silent] :as project-info} label v1 & v2]
     (when-not silent
       (apply println label v1 v2))
     (concat [v1] v2)))

(defn verbose-msg 
  [{:keys [verbose] :as project-info} label text]
  (when verbose
    (println "leiningen-resouce:" label text))
  text)

(defn err-msg [ & text]
  (apply println text)
  text)
  

(defn- plugin-values
  "Additional value available to the stencil renderer"
  []
  {
   :timestamp (java.util.Date.)
   })


(defn- name-to-key [^String name]
  (let [rtnval (vec (map keyword (.split name "[.]")))]

    rtnval))

(defn- system-properties-seq []
  (for [ [key value] (System/getProperties)]
    [(name-to-key key) value ]))

(defn- assoc-in-from-vector [m [keyvec val]]
  (let [keyvec (conj keyvec :prop)
        rtnval (assoc-in m keyvec  val)]
    rtnval))

(defn- system-properties "Load in the system properties.  Convert . to nested maps"
  []
  (reduce assoc-in-from-vector {} (system-properties-seq)))

(defn re-matches-any [regex-seq val]
  (some #(re-matches % val) regex-seq))

(defn pprint 
  "Dump out the map of values passed to stencil"
  [value-map]
  (pprint/pprint value-map)
  (flush))

;; ## FileSpec
;; src - the source file as a string
;; src-file - the source file as a File
;; dest - the destination of the file
;; resource-path [ source-spec option]
;; dest-file - dest as a File 
;; skip - if true, do not pass the file through stencil
;; update - if true, only copy more recent files

(defrecord FileSpec [src src-file dest resource-path dest-file skip update])

(defn include-file? 
  "Take a FileSpec and check if that file should be included.  

  Return the name of the file or nil if it is not to be included."
[{:keys [src resource-path]}]
  (let [ [_ {:keys [includes excludes]}] resource-path]
    ;(println "include-file?: " includes excludes fname)
    (if (re-matches-any includes src)
      (if-not (re-matches-any excludes src)
        src))))
(defn echo [label s]
  (println label s)
  s)

(defn all-file-specs
  "Take the directories mentioned in 'resource-paths' and get all the
files in those directories as a seq.  

Return a FileSpec"

  [{:keys [resource-paths update] :as project-info}]
  (verbose-msg project-info "resource-paths" resource-paths)
  (for [[source-path {:keys [target-path]} :as resource-path] resource-paths
        ^java.io.File file (file-seq (io/file (verbose-msg project-info "source-path"  source-path)))
        :when (.isFile file)]
    (let [dest-file (dest-from-src source-path target-path file)
          dest (.getPath dest-file)]
      (FileSpec.  (.getPath file) file dest resource-path dest-file false update))))




;; A resource path is either a string `src` or a pair
;;
;;    [ "src" {:includes [] :excludes [] :target-path "target"} ]

(defn normalize-resource-paths 
  "Resource paths can be passed as strings or as vectors of 2
  elements: the source path and the options.  Convert the former to the latter."
  [resource-paths includes excludes target-path]
  (let [default-options {:includes includes :excludes excludes :target-path target-path}]
    (for [resource-path resource-paths]
      (let [resource-path (if (string? resource-path) 
                            [ resource-path {} ]
                            resource-path)
            [source-path options] resource-path]
        [source-path (merge default-options options)]))))


(defrecord ProjectInfo [resource-paths target-path value-map includes excludes skip-stencil update silent verbose])

(defn update-file-spec [skip-stencil {:keys[ src dest] :as file-spec}]
  (let [skip (re-matches-any skip-stencil src)]
    (assoc file-spec :skip skip)))

;; ## file spec seq
;; Take in a `ProjectInfo` and return a seq of `FileSpec`

(defn file-spec-seq [{:keys [skip-stencil] :as project-info}]
  (->> (all-file-specs project-info)
       (verbose-msg project-info "all-file-specs")
       (filter include-file?)
       (verbose-msg project-info "POST include-file?")
       (map (partial update-file-spec skip-stencil))))

;; ## clean file spec
;; Expect a file-spec
(defn clean-file-spec [ {:keys[dest-file]} ] 
  (when (.exists dest-file)
    (.delete dest-file)
    (loop [parent (.getParentFile dest-file)]
      (when (and parent
                 (.isDirectory parent)
                 (not (seq (.list parent))))
        (when  (.delete parent)
          (recur (.getParentFile parent)))))
    dest-file))

;; ## clean task

(defn clean-task 
  "Remove the files created by executing the resource plugin."
  [project-info]
  (every? identity (->> (file-spec-seq project-info)
                        (map clean-file-spec))))

;; ## copy file spec
;; Expect a file-spec
;[src src-file dest resource-path dest-file skip update]
(defn copy-file-spec [ {:keys[value-map silent] :as project-info} 
                       {:keys[src src-file dest-file update skip]} ] 
  (verbose-msg project-info "***** copy-file-spec:" (str "SRC:" src-file "DEST:" dest-file))
  (if (not (.exists src-file))
    (err-msg "Missing source file:" src)
    (let [dest-ts (.lastModified dest-file)
          src-ts (.lastModified src-file)]
      (when (or (not update)
                (and update (<  dest-ts src-ts)))
        (msg project-info "Copy" src "to" (str dest-file))
        (let [s (if-not skip
                  (stencil/render-string (slurp src) value-map)
                  (io/file src))]
          (io/make-parents dest-file)
          (io/copy s dest-file)
          (.setExecutable dest-file (.canExecute src-file))
          ;(println "hhhhh" s src-file dest-file)
          [src-file dest-file])))))

;; ## copy task

(defn copy-task 
  "Remove the files created by executing the resource plugin."
  [project-info]
  (verbose-msg project-info "copy task" "START")
  (every? identity (->> (file-spec-seq project-info)
                        (verbose-msg project-info "file-spec-seq")
                        (map (partial copy-file-spec project-info)))))

;; ## resource
;; This is the main entry point into the plugin.  It supports 3 tasks:
;; copy, clean and pprint.
;;
;; This function creates the `ProjectInfo`, determines which task is
;; needs and calls it.

(defn resource
  "Task name can also be pprint or clean"
  [project & task-keys]
  (stencil.loader/set-cache {})
  (let [{:keys [resource-paths target-path extra-values excludes includes 
                skip-stencil update silent verbose]
         :or {update false
              excludes []
              includes [#".*"]
              silent false
              verbose false
              skip-stencil []
              target-path (:target-path project)
              resource-paths nil
              extra-values nil}} (:resource project)]
    (let [value-map (merge {} project (plugin-values) (system-properties) extra-values)
          resource-paths (normalize-resource-paths resource-paths includes excludes target-path)
          task-name (first task-keys)
          project-info (ProjectInfo. resource-paths target-path value-map includes excludes skip-stencil update silent verbose)]
      (verbose-msg project-info "project-info" project-info)
      ;;(println "TASK:" task-name)
      (cond
       (= "pprint" task-name) (pprint value-map)
       (= "clean" task-name) (clean-task project-info)
       (= "copy" task-name) (copy-task project-info)
       :else (copy-task project-info)))))

(defn compile-hook [task & [project & more-args :as args]]
  (msg (:silent project) "Copying resources...")
  (resource project)
  (apply task args))

(defn clean-hook [task & [project & more-args :as args]]
  (msg (:silent project) "Removing copied resources...")
  (resource project "clean")
  (apply task args))

;;
;; Borrowed from https://github.com/emezeske/lein-cljsbuild/blob/master/plugin/src/leiningen/cljsbuild.clj

(defn activate
  "Set up hooks for the plugin. Eventually, this can be changed to just hook,
and people won't have to specify :hooks in their project.clj files anymore."
  []
  (hooke/add-hook #'lcompile/compile #'compile-hook)
  (hooke/add-hook #'lclean/clean #'clean-hook)
)

;; Lein1 hooks have to be called manually, in lein2 the activate function will
;; be automatically called.
;; Borrowed from https://github.com/emezeske/lein-cljsbuild/blob/master/plugin/src/leiningen/cljsbuild.clj
(when-not lein2? (activate))

(def end-of-file true)
