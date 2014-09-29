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

(def project-update
  {
   :resource  {:resource-paths ["test-resources/testupdate"]
               :target-path "target/testupdate"
               :excludes [#".*~"]
               :extra-values { mykey myval }}})

(deftest test-paths
  (resource project))

(deftest test-update
  "Validate that the file is not copied by creating a file and then trying to copy over it"
  (let [src-file (java.io.File. "test-resources/testupdate/updatesrc.txt")
        dest-file (java.io.File. "target/testupdate/updatesrc.txt")
        dest-file2 (java.io.File. "target/testupdate/updatesrc2.txt")
        value-map {}
        skip-stencil true
        update true
        src (.getPath src-file)
        now (java.util.Date.)]
    (resource project-update "clean")
    (ensure-directory-exists dest-file2)
    (spit dest-file2 now)
    (is (= [src-file dest-file] (copy src dest-file value-map skip-stencil update src-file)))
    (is (not (copy src dest-file2 value-map skip-stencil update src-file)))
    (is (= [src-file dest-file2] (copy src dest-file2 value-map skip-stencil false src-file)))))

(deftest test-clean
  (resource project "clean"))

(deftest test-pprint
  (let [^String r (with-out-str (resource project "pprint"))]
    (is (.contains r (str mykey)))))
