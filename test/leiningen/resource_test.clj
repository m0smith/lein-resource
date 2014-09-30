(ns leiningen.resource-test
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :as ct]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [leiningen.resource :refer :all]))


(defn gen-nil-less-map [m] (gen/fmap (fn [f] (into {} (remove (comp nil? second) f))) m))

(def gen-regex (gen/elements [ #"^.*~$" #".html$"]))

(def gen-source-path gen/string-ascii)
(def gen-includes (gen/one-of [(gen/return nil) (gen/list gen-regex)]))
(def gen-excludes (gen/one-of [(gen/return nil) (gen/list gen-regex)]))
(def gen-target-path gen/string-ascii)
(def gen-options (gen-nil-less-map (gen/hash-map :includes gen-includes
                                                 :excludes gen-excludes
                                                 :target-path gen-target-path)))
(def gen-resource-path (gen/tuple gen-source-path gen-options))
(def gen-resource-paths (gen/list (gen/one-of [gen-source-path gen-resource-path])))

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


;; ## Properties
;; The output is a seq of [ src options ]

(ct/defspec test-normalize-resource-paths 100
  (prop/for-all [[paths [source-paths & args]] gen-nomalize]
                (every? identity 
                        (map (fn [[new-path] path]
                               (= (if (string? path) path (first path)) new-path))
                             paths source-paths))))


(ct/defspec test-include-file? 100
  (prop/for-all [[rtnval [source-path resource-path]] gen-include-file?]
                (println rtnval source-path resource-path)))
  
                                  
                  
                
                
