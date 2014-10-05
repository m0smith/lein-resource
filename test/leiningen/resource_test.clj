(ns leiningen.resource-test
  (:import [leiningen.resource.FileSpec])
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :as ct]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.java.io :as io]
            [leiningen.resource :refer :all]))


(defn gen-nil-less-map [m] (gen/fmap (fn [f] (into {} (remove (comp nil? second) f))) m))


(def gen-value-map (gen/return {}))
(def gen-skip-stencil (gen/return false))
(def gen-update (gen/return false))
(def gen-regex (gen/elements [ #"^.*~$" #".html$" #".css$" #".xml$"]))
(def gen-extension (gen/elements [ "~" ".html" ".css" ".xml"]))

(def gen-path (gen/fmap (fn [f] (apply str (interpose "/" f))) 
                        (gen/list (gen/not-empty gen/string-alpha-numeric))))
(def gen-source-path gen-path)
(def gen-target-path gen-path)

                               
(def gen-includes (gen/one-of [(gen/tuple (gen/return #"^.*$")) 
                               (gen/list gen-regex)]))
(def gen-excludes (gen/one-of [(gen/return nil) (gen/list gen-regex)]))


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

(def gen-source-tree
  (gen/fmap (fn [[root files]]
              (doseq[[f ext] files]
                (io/make-parents root f)
                (spit (io/file root  (str f ext)) "hamsters love you"))
              root)
            (gen/tuple
             gen-source-tree-root 
             (gen/list (gen/tuple
                        (gen/not-empty gen/string-alpha-numeric)
                        gen-extension)))))

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



;;  FileSpec [src src-file dest resource-path dest-file skip])
;;
(def gen-file-spec 
  (gen/fmap (fn [[src resource-path skip]] 
              (let [src-file (io/file src)
                    [_ {:keys [target-path]}] resource-path
                    dest target-path
                    dest-file (io/file dest)
                    ]
                (->FileSpec src src-file dest resource-path dest-file skip)))
            (gen/tuple gen-source-path 
                       gen-resource-path
                       gen-skip-stencil)))

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
                      target-path (if (and (= \/ (last target-path))
                                           (> count-target-path 1))
                                    (subs target-path 0 (dec count-target-path))
                                    target-path)]
                  (if (= 0 count-path count-target-path)
                    (= p "/")
                    (if (= 0 count-path)
                      (= p target-path)
                      (= (clojure.string/replace (str target-path "/" path) 
                                                 #"/+" "/")
                         p))))))


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

(defn file-spec-consistant? [{:keys[src src-file dest dest-file resource-path] :as file-spec}]
  (let [[source-path {:keys[target-path] :as options}] resource-path]
    (and
     (is (= src-file (io/file src)))
     (is (string? src))
     (is (string? dest))
     (is (instance? java.io.File src-file))
     (is (instance? java.io.File dest-file))
     (is (.startsWith src source-path))
     (is (.startsWith  dest target-path))
     )))

(def gen-all-file-specs
  (gen/fmap (fn [args] [(apply all-file-specs args) args])
            (gen/tuple gen-resource-paths-od)))

(ct/defspec test-all-file-specs 50
  (prop/for-all [[rtnval [args]] gen-all-file-specs]
                (is (every? #(instance? leiningen.resource.FileSpec %) rtnval))
                (every? file-spec-consistant?  rtnval)))
