(ns leiningen.resource-test
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

(def gen-includes (gen/one-of [(gen/return #"^.*$") (gen/list gen-regex)]))
(def gen-excludes (gen/one-of [(gen/return nil) (gen/list gen-regex)]))
(def gen-target-path gen/string-ascii)

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
(def gen-resource-paths-od (gen/list 
                            (gen/one-of [gen-source-tree gen-resource-path-od])))


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


;; [src src-file dest resource-path]
(def gen-file-spec (gen/fmap (fn [m] (merge m {:src-file (java.io.File. (:src m))}))
                             (gen/hash-map :src gen-source-path
                                           :dest gen-target-path
                                           :resource-path gen-resource-path)))

(def gen-nomalize 
  (gen/fmap (fn [args] [(apply normalize-resource-paths args) args])
            (gen/tuple gen-resource-paths gen-includes gen-excludes gen-target-path)))


(def gen-include-file? 
  (gen/fmap (fn [args] [(apply include-file? args) args])
            (gen/tuple gen-file-spec)))

(def gen-dest-from-src 
  (gen/fmap (fn [[sp tp p :as args]] [(dest-from-src sp tp (io/file sp p)) args])
            (gen/tuple gen-source-path gen-target-path gen-path)))


;; ## normalize-resource-paths
;; The output is a seq of [ src options ]

(ct/defspec test-normalize-resource-paths 50
  (prop/for-all [[paths [source-paths & args]] gen-nomalize]
                (every? identity 
                        (map (fn [[new-path] path]
                               (= (if (string? path) path (first path)) new-path))
                             paths source-paths))))

;; ## dest-from-src
;; The result will have duplicate / removed as well as a trailing / 

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

  
                                  
                  
                
                
