(ns leiningen.resource-test
  (:import [leiningen.resource.FileSpec])
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :as ct]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.java.io :as io] 
            [clojure.core.async :as async ]
            [leiningen.resource :refer :all]))

(def sep java.io.File/separator)


;; Stolen from the old clojure contrib:  
;; It was left out of clojure.java.io because in Java 6 there's no way to
;; detect whether a given directory is actually a symlink, so it has to
;; recurse into symlinked directories indiscriminately. This behaviour is
;; arguably dangerous. Perhaps in the far future it could be added once
;; support for Java 6 is dropped.
;;
;; Not generating symlinks so it is safe in this context


(defn delete-file-recursively
  "Delete file f. If it's a directory, recursively delete all its contents.
  Raise an exception if any deletion fails unless silently is true."
  [f & [silently]]
  (let [f (io/file f)]
    (if (.isDirectory f)
      (doseq [child (.listFiles f)]
        (delete-file-recursively child silently)))
    (when (.exists f)
      (io/delete-file f silently))))


(defn gen-nil-less-map [m] (gen/fmap (fn [f] (into {} (remove (comp nil? second) f))) m))

(def gen-value-map (gen/return {}))
(def gen-update (gen/return false))
(def gen-regex (gen/elements [ #"^.*~$" #".html$" #".css$" #".xml$"]))
(def gen-extension (gen/elements [ "~" ".html" ".css" ".xml"]))
(def gen-skip (gen/elements [true false]))
(def gen-path (gen/fmap (fn [f] (apply str (interpose sep f))) 
                        (gen/list (gen/not-empty gen/string-alpha-numeric))))
(def gen-source-path gen-path)
(def gen-target-path gen-path)

(def gen-file-content-part 
  (gen/one-of 
   [
    (gen/elements [ "STUFF" " " \tab \newline "HAMSTER" "{{V1}}" "{{V2}}"])
    gen/string]))
   
(def gen-file-content
  (gen/fmap (partial apply str)
            (gen/not-empty (gen/list gen-file-content-part))))

(def gen-file-name 
  (gen/fmap (partial apply str)
            (gen/tuple 
             (gen/not-empty gen/string-alpha-numeric) 
             gen-extension)))
(def gen-file-names (gen/not-empty (gen/list gen-file-name)))
                               
(def gen-includes (gen/one-of [(gen/tuple (gen/return #"^.*$")) 
                               (gen/list gen-regex)]))
(def gen-excludes (gen/one-of [(gen/return nil) (gen/list gen-regex)]))
(def gen-skip-stencil (gen/one-of [(gen/return nil) (gen/list gen-regex)]))


;; ## Generate a Source Tree
;; * a sub-directory of target/lein-resource/tmp
;; * must be new


(defn exists? [f]
  (.exists (io/file f)))

(def not-exists? (comp not exists?))

(defn gen-tree-root [root]
  (gen/such-that not-exists? 
                 (gen/fmap (fn[f] (str root f))
                           (gen/not-empty gen/string-alpha-numeric))))

(def gen-source-tree-root
  (gen/fmap (fn [f] (io/make-parents (io/file f "dummy")) f)
            (gen-tree-root "target/lein-resource/source/")))

(def gen-dest-tree-root
  (gen/fmap (fn [f] 
              (io/make-parents (io/file f "dummy"))
              f) (gen-tree-root "target/lein-resource/dest/")))


(defn make-path-od 
  ([root file ext content] (make-path-od root (str file ext) content))
  ([root file content]
     (io/make-parents root file)
     (spit (io/file root file) content))
  ([file content]
     ;;(println file)
     (io/make-parents file)
     (spit (io/file file) content)))

(def gen-source-tree
  (gen/fmap (fn [[root files]]
              (doseq[[f ext content] files]
                (make-path-od root f ext content))
              root)
            (gen/tuple
             (gen/not-empty gen-source-tree-root )
             (gen/list (gen/tuple
                        (gen/not-empty gen/string-alpha-numeric)
                        gen-extension
                        gen-file-content)))))

;; ## Resource Path In Memory

(def gen-options (gen-nil-less-map 
                  (gen/hash-map :includes gen-includes
                                :excludes gen-excludes
                                :target-path gen-target-path)))

(def gen-resource-path (gen/tuple gen-source-path gen-options))
(def gen-resource-paths (gen/list (gen/one-of [gen-source-path gen-resource-path])))

;; ## Resource Path On Disk
;;  Must be manually cleaned up

(def gen-options-od (gen-nil-less-map 
                     (gen/hash-map :includes gen-includes
                                   :excludes gen-excludes
                                   :target-path gen-dest-tree-root)))

(def gen-resource-path-od (gen/tuple gen-source-tree gen-options-od))
(def gen-resource-paths-od (gen/list gen-resource-path-od))


;; ## Project Info
;; (defrecord ProjectInfo [resource-paths target-path value-map includes excludes skip-stencil update])

(def gen-project-info
  (gen/fmap  (partial apply ->ProjectInfo)
             (gen/tuple
              gen-resource-path
              gen-target-path
              gen-value-map
              gen-includes
              gen-excludes
              gen-skip-stencil
              gen-update)))

(def gen-project-info-od
  (gen/fmap  (partial apply ->ProjectInfo)
             (gen/tuple
              gen-resource-path-od
              gen-dest-tree-root
              gen-value-map
              gen-includes
              gen-excludes
              gen-skip-stencil
              gen-update)))



;;  FileSpec [src src-file dest resource-path dest-file skip update])
;;
(def gen-file-spec 
  (gen/fmap (fn [[src resource-path skip update]] 
              (let [src-file (io/file src)
                    [_ {:keys [target-path]}] resource-path
                    dest target-path
                    dest-file (io/file dest)
                    ]
                (->FileSpec src src-file dest resource-path dest-file skip update)))
            (gen/tuple gen-source-path 
                       gen-resource-path
                       gen-skip
                       gen-update)))

(def gen-file-spec-od
  (gen/fmap (fn [[file dfile resource-path skip update content]] 
              (let [ [root] resource-path
                    src-file (io/file root file)
                    src (.getPath src-file)
                    [_ {:keys [target-path]}] resource-path
                    dest-file (io/file target-path dfile)
                    dest (.getPath dest-file)
                    ]
                (make-path-od src-file content)
                (->FileSpec src src-file dest resource-path dest-file skip update)))
            (gen/tuple (gen/not-empty gen/string-alpha-numeric)
                       (gen/not-empty gen/string-alpha-numeric)
                       gen-resource-path-od
                       gen-skip
                       gen-update
                       gen-file-content)))

(def gen-normalize 
  (gen/fmap (fn [args] [(apply normalize-resource-paths args) args])
            (gen/tuple gen-resource-paths gen-includes gen-excludes gen-target-path)))


(def gen-dest-from-src 
  (gen/fmap (fn [[sp tp p :as args]] [(dest-from-src sp tp (io/file sp p)) args])
            (gen/tuple gen-source-path gen-target-path gen-path)))


;; ## normalize-resource-paths
;; The output is a seq of [ src options ]

(defn source-path-from-raw-resource-path [resource-path]
  (if (string? resource-path) 
    resource-path 
    (first resource-path)) )

(defn target-path? [resource-path raw-resource-path default-target-path]
  (let [target-path (-> resource-path second :target-path)
        raw-target-path (-> raw-resource-path second :target-path)]
    (if raw-target-path
      (is (= target-path raw-target-path))
      (is (= target-path default-target-path)))))

(ct/defspec test-normalize-resource-paths 50
  (prop/for-all [[resource-paths [raw-resource-paths include excludes target-path]] gen-normalize]
                (every? identity
                 (map (fn [rp raw-rp]
                        (and 
                         (is (first rp))
                         (target-path? rp raw-rp target-path)
                         (is (= (first rp) (source-path-from-raw-resource-path raw-rp)))))
                      
                      resource-paths raw-resource-paths))))

;; ## dest-from-src
;; The result will have duplicate / removed as well as a trailing / 
;; An empty file is set to /



(ct/defspec test-dest-from-src 100
  (prop/for-all [[rtnval [source-path target-path path]] gen-dest-from-src]
                (let [p (.getPath rtnval)
                      count-path (count path)
                      count-target-path (count target-path)
                      target-path (if (and (= (first sep) (last target-path))
                                           (> count-target-path 1))
                                    (subs target-path 0 (dec count-target-path))
                                    target-path)]
                  (if (= 0 count-path count-target-path)
                    (is (= p sep))
                    (if (= 0 count-path)
                      (is (= p target-path))
                      (let [full-path (str target-path sep path)
                            dup-regex (re-pattern (str "(\\" sep ")+"))]
                        ;(println dup-regex full-path sep p)
                        (is (= (clojure.string/replace full-path dup-regex "$1")
                               p))))))))


;; ## include-file?
;; Properties
;; * A non-nil means the source matches an include
;; * A non-nil means the source does not match an include
;; * A nil return could either be a no includes match or an exlcude matches

(def gen-include-file? 
  (gen/fmap (fn [args] [(apply include-file? args) args])
            (gen/tuple gen-file-spec)))

(ct/defspec test-include-file? 100
  (prop/for-all [[rtnval [args]] gen-include-file?]
                (let [{:keys [src resource-path]} args
                      [_ {:keys [includes excludes]}] resource-path]
                  (if rtnval
                    (and (is (re-matches-any includes src))
                         (is (not (re-matches-any excludes src))))
                    (is (or (not (re-matches-any includes src))
                            (re-matches-any excludes src)))))))
                             
;; ## all-file-specs
;; It returns a seq of FileSpec

(defn starts-with [string pre]
  (let [string (clojure.string/replace string #"\\" "/")]
    (.startsWith string pre)))

(defn file-spec-consistant? [{:keys[src src-file dest dest-file resource-path] :as file-spec}]
  (let [[source-path {:keys[target-path] :as options}] resource-path]
    (and
     (is (= src-file (io/file src)))
     (is (string? src))
     (is (string? dest))
     (is (instance? java.io.File src-file))
     (is (instance? java.io.File dest-file))
     (is (starts-with src source-path))
     (is (starts-with  dest target-path))
     )))

(defn mark-for-deletion [ch [source-path {:keys [target-path]}]]
  (async/go (async/>! ch source-path) (async/>! ch target-path)))

(def gen-all-file-specs
  (gen/fmap (fn [args] [(apply all-file-specs args) args])
            (gen/tuple gen-resource-paths-od gen-update)))

(ct/defspec test-all-file-specs 50
  (prop/for-all [[rtnval [resource-paths]] gen-all-file-specs]
                (let [ch (async/chan)]
                  (doseq [ rp resource-paths] (mark-for-deletion ch rp))
                  (is (every? #(instance? leiningen.resource.FileSpec %) rtnval))
                  (every? file-spec-consistant? rtnval)
                  (async/go (while true 
                              (delete-file-recursively (async/<! ch)))))))


;; ## clean-file-spec
;; Propreties
;;  * Delete the file specified in dest-file
;;  * Delete empty parent directories
;;  * Do not delete other files in the same directory


;; Create a directory with 1 or more files
(def gen-files-for-clean-file-spec
  (gen/fmap (fn [[root files]]
              (for [file files]
                (let [_ ( make-path-od root file "From gen-clean-file-spec" )]
                  [root file])))
                             
            (gen/tuple
             gen-dest-tree-root
             gen-file-names)))

(ct/defspec test-clean-file-spec 50
  (prop/for-all [files gen-files-for-clean-file-spec]
    (and (every? identity
                 (for [[root file :as args] files]
                   (let [dest-file (io/file root file)]
                     (clean-file-spec {:dest-file dest-file})        
                     (is (not (.exists dest-file))))))
         (let [dest-file (apply io/file (first files))
               parent (.getParentFile dest-file)]
           (is (not (.exists parent)))))))
           
;; ## copy file spec
;; Properties
;; * A file exists in the target    

(ct/defspec test-copy-file-spec 50
  (prop/for-all [{:keys [dest-file resource-path] :as file-spec} gen-file-spec-od]
                (let [ch (async/chan)]                
                  (mark-for-deletion ch resource-path)
                  (copy-file-spec {} file-spec)
                  (is (exists? dest-file))
                  (async/go (while true 
                              (delete-file-recursively (async/<! ch)))))))



                
   
   

