(ns plugin.test.core
  (:require [clojure.java.io :as io])
  (:use [leiningen.resource])
  (:use [clojure.test]))

(def eol (System/getProperty "line.separator"))

(def myval "value")
(def mykey :key)

(def project
  {
   :resource  {:resource-paths [
                                "test-resources/test1"
                                ["test-resources/test1" {:extra-values {:key "DEV"}
                                                         :target-path "target/test1-DEV"}]
                                ["test-resources/test1" {:extra-values {:key "PROD"}
                                                         :target-path "target/test1-PROD"}]
                                ]
               :target-path "target/test1"
               :excludes [#".*~"]
               :extra-values { mykey myval }}})

(def project-update
  {
   :resource  {:resource-paths ["test-resources/testupdate"]
               :target-path "target/testupdate"
               :excludes [#".*~"]
               :extra-values { mykey myval }}})

(deftest test-paths
  (resource project)
  (is (.startsWith (slurp "target/test1/subdir/test.txt") myval))
  (is (.startsWith (slurp "target/test1-DEV/subdir/test.txt") "DEV"))
  (is (.startsWith (slurp "target/test1-PROD/subdir/test.txt") "PROD")))


(deftest test-clean
  (resource project "clean")
  (is (not (.exists (io/file "target/test1/subdir/test.txt"))))
  (is (not (.exists (io/file "target/test1-DEV/subdir/test.txt"))))
  (is (not (.exists (io/file "target/test1-PROD/subdir/test.txt")))))

(deftest test-pprint
  (let [^String r (with-out-str (resource project "pprint"))]
    (is (.contains r (str mykey)))))

(deftest test-print
  (let [out-str (with-out-str (resource project "print" "{{resource.target-path}}={{resource.extra-values.key}}"))
        expected (str (get-in project [:resource :target-path]) "="
                      (get-in project [:resource :extra-values mykey]) eol)]
    (is (= expected out-str))))
