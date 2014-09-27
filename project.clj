
(defproject lein-resource "0.3.8-SNAPSHOT" 
  :description 
  "
A task that copies the files for the resource-paths to the
target-path, applying stencil to each file allowing the files to be
updated as they are copied.

## Usage

### Copy
Execute the plugin to copy the files.

     lein resource 



### Clean
Remove the files created by the plugin.

     lein resource clean 


### Pretty Print
Dump the map of values sent to stencil.

     lein resource pprint  

## Configuration

To configure the plugin,add to the project.clj:

### Sample Configuration

     :resource {
         ;; required or will do nothing
         :resource-paths [\"src-resource\"]
 
         ;; optional default to the global one
         :target-path \"target/html\"   

         ;; When true, only copy files where src is 
         ;; newer than default
         :update false

         ;; optional - this is the default  
         :includes [ #\".*\" ]   

         ;; optional - default is no excludes 
         ;; which is en empty vector
         :excludes [ #\".*~\" ]
   
         ;; optional - default to nil
         :extra-values 
           { :year 
             ~(.get (java.util.GregorianCalendar.)
                    (java.util.Calendar/YEAR)) }  
"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [bultitude "0.2.4"]  ;; namespace finder
                 [org.clojure/data.priority-map "0.0.5"]
                 [marginalia "0.8.0"] 
                 [org.clojure/test.check "0.5.9"] ;; property testing
                 [stencil "0.3.3" :exclusions [org.clojure/core.cache]]
                 ]
  :plugins [[lein-pprint "1.1.1"]]
  :scm {:url "git@github.com:m0smith/lein-resource.git"}
  :url "https://github.com/m0smith/lein-resource"
  :eval-in-leiningen true
  :pom-addition [:developers [:developer
                              [:name "Matthew O. Smith"]
                              [:url "http://m0smith.com"]
                              [:email "matt@m0smith.com"]
                              [:timezone "-7"]]])
