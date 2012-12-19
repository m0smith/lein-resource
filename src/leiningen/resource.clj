(ns leiningen.resource
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [stencil.core :as stencil]))


(defn- dest-from-src
  "Change a src file to a dest file by replacing the directory"
  [src-path dest-path ^java.io.File f]
  (let [s (count src-path)
        src (.getPath f)
        fnamex (subs src  s)
        fname (if (#{\/} (first fnamex)) (subs fnamex 1) fnamex)]
    (io/file dest-path fname)))

(defn- all-file-pairs
  "Take the directories mentioned in 'resource-paths' and get all the
files in those directories as a seq.  Return a seq of 2 element
vectors. The first is the source file and the second is the
destination file."  [resource-paths target-path]
  (for [resource-path resource-paths
        ^java.io.File file (file-seq (io/file resource-path))
        :when (.isFile file)]
    [file (dest-from-src resource-path target-path file)]))

(defn- plugin-values
  "Additional value available to the stencil renderer"
  []
  {
   :timestamp (java.util.Date.)
   })


(defn- name-to-key [^String name]
  (let [rtnval (vec (map keyword (.split name "[.]")))]

    rtnval))

(defn- system-properties-seq []
  (for [ [key value] (System/getProperties)]
    [(name-to-key key) value ]))

(defn- assoc-in-from-vector [m [keyvec val]]
  (let [keyvec (conj keyvec :prop)
        rtnval (assoc-in m keyvec  val)]
    rtnval))

(defn- system-properties "Load in the system properties.  Convert . to nested maps"
  []
  (reduce assoc-in-from-vector {} (system-properties-seq)))

(defn- pprint "Dump out the map that is passed to stencil"
  [value-map]
  (pprint/pprint value-map)
  (flush))

(defn- resource* ""
  [resource-paths target-path value-map]
      (let [files (all-file-pairs resource-paths target-path)]
        (doseq [[^java.io.File
                 src dest] files]
          (let [fname (.getPath src)
                s (stencil/render-string (slurp fname) value-map)]
            (io/copy s (io/file dest))))))

(defn resource
  "A task that copies the files for the resource-paths to the target-path, applying stencil
to each file allowing the files to be updated as they are copied."
  [project & keys]
  (let [{:keys [resource-paths target-path extra-values]
         :or {target-path (:target-path project)
              resource-paths nil
              extra-values nil}} (:resource project)]
    (let [value-map (merge {} project (plugin-values) (system-properties) extra-values)]
      (cond
       (first keys) (pprint value-map)
       :else (resource* resource-paths target-path value-map)))))
