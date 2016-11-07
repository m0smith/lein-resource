(defproject lein-resource "16.11.1" 
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


###  Print
Print the value of a stencil specified as an argument, useful for build scripts

     lein resource print  \"{{version}}\"
     export MY_PROJECT_VERSION=$(lein resource print \"{{name}}:{{version}}\")

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
                             :extra-values {} ;; override the top level 
                                              ;; values for this resource
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

         ;; optional - list of regex of files to skip stencil
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
  :dependencies [
                 [stencil "0.5.0" :exclusions [org.clojure/core.cache org.clojure/clojure]]
                 
                 
                 [bultitude "0.2.8" :exclusions [org.clojure/clojure]]  ;; namespace finder
                 
                 ;[org.clojure/clojure "1.9.0-alpha13"]
                 ;[org.clojure/clojure "1.7.0"]
                 ]
  :profiles
  {
   :dev 
   { :dependencies [[org.clojure/core.async "0.2.385" :exclusions [org.clojure/clojure]]
                    [michaelblume/marginalia "0.9.0" :exclusions [org.clojure/clojure]] 
                    [org.clojure/test.check "0.9.0" :exclusions [org.clojure/clojure]] ;; property testing
      
                              ]}}
  
            
  :plugins [[lein-pprint "1.1.2" :exclusions [org.clojure/clojure]]]
  :scm {:url "git@github.com:m0smith/lein-resource.git"}
  :url "https://github.com/m0smith/lein-resource"
  :eval-in-leiningen true
  :pom-addition [:developers [:developer
                              [:name "Matthew O. Smith"]
                              [:url "http://m0smith.com"]
                              [:email "matt@m0smith.com"]
                              [:timezone "-7"]]])
