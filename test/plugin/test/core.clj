(ns plugin.test.core
  (:use [leiningen.resource])
  (:use [clojure.test]))

(def val "value")
(def key :key)

(def project
  {
   :resource  {:resource-paths ["test-resources/test1"]
               :target-path "target/test1"
               :extra-values { key val }}})

(deftest test-paths
  (resource project))

(deftest test-pprint
  (let [r (with-out-str (resource project ["pprint"]))]))
