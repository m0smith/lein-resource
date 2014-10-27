
(defproject lein-resource "14.10.1" 
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
          OR
         :resource-paths [ [\"src-resource\" 
                            {
                             :includes []  ;; list of regex
                             :excludes []  ;; list of regex
                             :target-path \"\" ;; directory to store files
                              }]]
 
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

         ;; optioan - list of regex of files to skip stencil
         :skip-stencil [ ]

         ;; optional - if true, do not echo progress to the screen
         :silent false
   
         ;; optional - if true, echo lots of debug info
         :verbose false
   
         ;; optional - default to nil
         :extra-values 
           { :year 
             ~(.get (java.util.GregorianCalendar.)
                    (java.util.Calendar/YEAR)) }  
## Links

 ### Marginalia

[Marginalia](http://gdeer81.github.io/marginalia)
[Markdown](http://daringfireball.net/projects/markdown/syntax)
[MathJax](http://www.mathjax.org/)     

### test.check
[test.check](https://github.com/clojure/test.check)

"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [stencil "0.3.4" :exclusions [org.clojure/core.cache]]
                 ;[org.clojure/core.cache "0.6.4"]
                 [org.clojure/data.priority-map "0.0.4"]
                 [bultitude "0.2.6"]  ;; namespace finder
                 ;[org.clojure/data.priority-map "0.0.5"]

                 ]
  :profiles { :dev 
             { :dependencies [[org.clojure/core.async "0.1.346.0-17112a-alpha"]
                              [marginalia "0.8.0"] 
                              [org.clojure/test.check "0.5.9"] ;; property testing
                              ]}}
  
            
  :plugins [[lein-pprint "1.1.2"]]
  :scm {:url "git@github.com:m0smith/lein-resource.git"}
  :url "https://github.com/m0smith/lein-resource"
  :eval-in-leiningen true
  :pom-addition [:developers [:developer
                              [:name "Matthew O. Smith"]
                              [:url "http://m0smith.com"]
                              [:email "matt@m0smith.com"]
                              [:timezone "-7"]]])
