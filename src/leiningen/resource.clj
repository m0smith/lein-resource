(ns leiningen.resource
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
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

(defn copy 
  "Copy src to dest-file unless update is true and src is older than dest. When skip-stencil is true, do not process the file using stencil.

Return: 
    nil - no file copied due to update being set and the src older
          than the dest 
   [src-file dest-file] - when the file was copied
"
  [src dest-file value-map skip-stencil update src-file]
  ;(println src dest-file value-map skip-stencil update src-file )
  (if (not (.exists src-file))
    (println "Missing source file:" src)
    (let [dest-ts (.lastModified dest-file)
          src-ts (.lastModified src-file)]
      ;(println "update:" update " dest-ts:" dest-ts " src-ts:" src-ts)
      (when (or (not update)
                (and update (<  dest-ts src-ts)))
        (println "Copy" src "to" (str dest-file))
        (let [s (if-not skip-stencil
                  (stencil/render-string (slurp src) value-map)
                  (io/file src))]
          (io/make-parents dest-file)
          (io/copy s dest-file)
          ;(println "hhhhh" s src-file dest-file)
          [src-file dest-file])))))
  
(defn cleanxxx
  "Remove the files created by executing the resource plugin."
  [src ^java.io.File dest & args]
  (println "Remove "  (str dest))
  (when (.exists dest)
    (.delete dest)
    (loop [parent (.getParentFile dest)]
      (when (and parent
                 (.isDirectory parent)
                 (not (seq (.list parent))))
        ;;(println "Delete parent:" parent)
        (when  (.delete parent)
          (recur (.getParentFile parent)))))))

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

(defrecord FileSpec [src src-file dest resource-path dest-file skip])

(defn include-file? 
  "Take a FileSpec and check if that file should be included.  

  Return the name of the file or nil if it is not to be included."
[{:keys [src resource-path]}]
  (let [ [_ {:keys [includes excludes]}] resource-path]
    ;(println "include-file?: " includes excludes fname)
    (if (re-matches-any includes src)
      (if-not (re-matches-any excludes src)
        src))))


(defn all-file-specs
  "Take the directories mentioned in 'resource-paths' and get all the
files in those directories as a seq.  

Return a seq of 3 element vectors. 
  The first is the source file and
  The second is the destination file.
  The third is the resource-path"

  [resource-paths target-path]
  (for [[source-path options :as resource-path] resource-paths
        ^java.io.File file (file-seq (io/file source-path))
        :when (.isFile file)]
    (FileSpec.  (.getPath file) file (dest-from-src source-path target-path file) resource-path nil false)))


(defn- resource* 
  "
  includes - a seq of regex that files must match to be included
  excludes - a seq of regex.  A file matching the regex will be excluded"
  [task resource-paths target-path value-map def-includes def-excludes skip-stencil update]
      (let [file-specs (all-file-specs resource-paths target-path)]
        ;(println "resource*: files:" files)
        (doseq [file-spec file-specs]
          (when (include-file? file-spec)
            ;(println "resource*: file-spec:" file-spec)
            (let [^java.io.File dest-file (io/file (:dest file-spec))
                  skip (re-matches-any skip-stencil (:src file-spec))]
              (task (:src file-spec) dest-file value-map skip update (:src-file file-spec)))))))


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


(defrecord ProjectInfo [resource-paths target-path value-map includes excludes skip-stencil update])



(defn update-file-spec [skip-stencil {:keys[ src dest] :as file-spec}]
  (let[ dest-file (io/file dest)
       skip (re-matches-any skip-stencil src)]
    (assoc file-spec :dest-file dest-file :skip skip)))

;; ## file spec seq
;; Take in a `ProjectInfo` and return a seq of `FileSpec`

(defn file-spec-seq [{:keys [resource-paths target-path skip-stencil] :as project-info}]
  (->> (all-file-specs resource-paths target-path)
       (filter include-file?)
       (map (partial update-file-spec skip-stencil))))

;; ## clean file spec
;; Expect a file-spec
(defn clean-file-spec [{:keys[dest-file]}]
  (when (.exists dest-file)
    (.delete dest-file)
    (loop [parent (.getParentFile dest-file)]
      (when (and parent
                 (.isDirectory parent)
                 (not (seq (.list parent))))
        ;;(println "Delete parent:" parent)
        (when  (.delete parent)
          (recur (.getParentFile parent)))))))

;; ## clean task

(defn clean-task 
  "Remove the files created by executing the resource plugin."
  [project-info]
  (->> (file-spec-seq project-info)
       (map clean-file-spec)))

;; ## resource
;; This is the main entry point into the plugin.  It supports 3 tasks:
;; copy, clean and pprint.
;;
;; This function creates the `ProjectInfo`, determines which task is
;; needs and calls it.

(defn resource
  "Task name can also be pprint or clean"
  [project & task-keys]
  (let [{:keys [resource-paths target-path extra-values excludes includes 
                skip-stencil update]
         :or {update false
              excludes []
              includes [#".*"]
              skip-stencil []
              target-path (:target-path project)
              resource-paths nil
              extra-values nil}} (:resource project)]
    (let [value-map (merge {} project (plugin-values) (system-properties) extra-values)
          resource-paths (normalize-resource-paths resource-paths includes excludes target-path)
          task-name (first task-keys)
          project-info (ProjectInfo. resource-paths target-path value-map includes excludes skip-stencil update)]
      ;;(println "TASK:" task-name)
      (cond
       (= "pprint" task-name) (pprint value-map)
       (= "clean" task-name) (clean-task project-info)
       :else (resource* copy resource-paths target-path value-map includes excludes skip-stencil update)))))

(defn compile-hook [task & [project & more-args :as args]]
  (println "Copying resources...")
  (resource project)
  (apply task args))

(defn clean-hook [task & [project & more-args :as args]]
  (println "Removing copied resources...")
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
