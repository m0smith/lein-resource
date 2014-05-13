(ns plugin.test.target
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [leiningen.resource :as r]
            [clojure.java.io :as io]))

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
                
