(ns plugin.test.target
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [leiningen.resource :as r]
            [clojure.java.io :as io]))


(defn dirs [size]
  "
    [name [files] (sub-dirs)]
   "

  (cond
   (= size 0) (gen/tuple gen/string-alpha-numeric  
                         (gen/not-empty (gen/vector gen/string-alpha-numeric))
                         (gen/return (list)))
   :else (gen/tuple gen/string-alpha-numeric  
                    (gen/not-empty (gen/vector gen/string-alpha-numeric))
                    (gen/list (gen/resize (dec size) (gen/sized dirs))))))


;;;   :else (gen/tuple (gen/not-empty (gen/vector gen/string-alpha-numeric)) (gen/resize (dec size) (gen/sized dirs)))))


(defn tree

  "Usage:
      (gen/sample (gen/sized tree))
      (gen/sample (gen/sized (partial tree gen/int)))"

  ([size] (tree (gen/list  gen/string-alpha-numeric) size))
  ([leaf-gen size]
     (if (= size 0)
       leaf-gen
       (let [new-size (quot size 2)
             smaller-tree (gen/resize new-size (gen/sized (partial  tree leaf-gen)))]
         (gen/one-of ;; choose either a leaf, or a node
          [leaf-gen
           (gen/tuple leaf-gen
                      (gen/one-of [(gen/return nil) smaller-tree])
                      (gen/one-of [(gen/return nil) smaller-tree]))])))))

(def sort-idempotent-prop
  (prop/for-all [v (gen/vector gen/int)]
                (= (sort v) (sort (sort v)))))

(defn dir-tree-seq [f include-files? include-dirs?]
  (->> (file-seq (io/as-file f))
       (filter #(or (and include-dirs? (.isDirectory %)) (and include-files? (.isFile %))))
       (map #(.getPath %))))


(def single-target-prop
  (let [dirs (dir-tree-seq "test-resources" false true)
        files (dir-tree-seq "test-resources" true false)]
    
    (prop/for-all [[[a & rest :as f] b :as v] (gen/tuple (gen/such-that not-empty (gen/vector (gen/elements dirs)))
                                                         gen/string-alpha-numeric )]
                  (println "v=" v)
                  (println (map #(dir-tree-seq % true false) f))
                  (nil? (println f b (apply r/all-file-pairs v))))))
                
