(ns plugin.test.core
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

(deftest test-paths
  (resource project))

(deftest test-clean
  (resource project "clean"))

(deftest test-pprint
  (let [^String r (with-out-str (resource project "pprint"))]
    (is (.contains r (str mykey)))))
