(ns plugin.test.core
  (:require [clojure.java.io :as io])
  (:use [leiningen.resource])
  (:use [clojure.test]))

(def myval "value")
(def mykey :key)

(def project
  {
   :resource  {:resource-paths ["test-resources/test1"]
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
  (resource project))

(deftest test-clean
  (resource project "clean"))

(deftest test-pprint
  (let [^String r (with-out-str (resource project "pprint"))]
    (is (.contains r (str mykey)))))

(deftest test-print
  (let [out-str (with-out-str (resource project "print" "{{resource.target-path}}={{resource.extra-values.key}}"))
        expected (str (get-in project [:resource :target-path]) "="
                      (get-in project [:resource :extra-values mykey]) "\n")]
    (is (= expected out-str))))
