# lein-resource

[![Build Status](https://travis-ci.org/m0smith/lein-resource.svg?branch=master)](https://travis-ci.org/m0smith/lein-resource)

A plugin that can be used to copy files from mulitple source
directories to a target directory while maintaining the subdirecotries.  Also, each file
will by default be transformed using [stencil](https://github.com/davidsantiago/stencil).  The map 
that is passed to stencil contains a combination of:

* The project map
* The system properties (with .prop added to the name )
* Additional values (currently only :timestamp)
* Values set in the project.clj using :resource :extra-values

## Usage

To use from Leiningen add to `project.clj`:
```clojure
:plugins [ [lein-resource "17.06.1"] ] 
```
To have it run before the jar file creation:
```clojure
:prep-tasks ["javac" "compile" "resource"]
```
To have it run before compile and after clean:
```clojure
:hooks [leiningen.resource]
```
To configure lein-resource, add to `project.clj`
```clojure
:resource {

  :resource-paths ["src-resources"] ;; required or does nothing
  ; OR
  :resource-paths [ ["src-resource" 
                     {
                      :includes []  ;; list of regex
                      :excludes []  ;; list of regex
                      :target-path "" ;; directory to store files
                      :extra-values {} ;; override the top level
		                       ;; values for this resource		      
                    }]]

  :target-path "target/html" ;; optional default to the global one
  :update   false      ;; if true only process files with src newer than dest
  :includes [ #".*" ]  ;; optional - this is the default
  :excludes [ #".*~" ] ;; optional - default is no excludes which is en empty vector
  :silent false ;; if true, only print errors
  :verbose false ;; if true, print debugging information
  :skip-stencil [ #"src-resources/images/.*" ] ;; optionally skip stencil processing - default is an empty vector
  :extra-values { :year ~(.get (java.util.GregorianCalendar.)
                                   (java.util.Calendar/YEAR)) }  ;; optional - default to nil
}
```
If `:resource-paths` is not set or is nil, then it won't do anything

### Calling

To run to plugin directly simply enter

    lein resource

To see all the properties that are passed to stencil:

    lein resource pprint


To print the value of a stencil passed as an argument (useful for build scripts & testing templates)

     lein resource print  "{{version}}"
     export MY_PROJECT_VERSION=$(lein resource print "{{name}}:{{version}}")


To delete all the copied files and empty directories:

    lein resource clean

### Values passed to stencil

Note that stencil/mustache uses dot notation (.) for nested maps.  For example, to get the username 
system property use:

    {{user.name.prop}}

A file that uses this plugin would contain:

    #
    # {{description}}
    # Build date: {{timestamp}}
    # Built by: {{user.name.prop}}
    # Copyright {{year}}
    #

* description is pulled from the project.clj
* timestamp is pulled from the addition fields
* user.name.prop is pulled from the system properties.  The system properties always have .prop on the end
* year is pulled from the `exta-values` defined in the project.clj

To see it in action, see the [Tic Tac Toe project.clj](https://github.com/m0smith/tic-tac-toe/blob/master/project.clj)

## Contributors
* Matthew O. Smith - original developer
* Kevin Bell - added [skip-stencil option](https://github.com/m0smith/lein-resource/pull/6)
* Jim Crossley- [only log when copy occurs](https://github.com/m0smith/lein-resource/pull/10)
* Cameron Dorrat - added the [print task](https://github.com/m0smith/lein-resource/pull/17)

To contribute, create a pull request.  Be sure to include unit tests for changes.

## License

Copyright &copy; 2015 Matther O. Smith

Distributed under the Eclipse Public License, the same as Clojure.
