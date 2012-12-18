# lein-stencil

A plugin that can be used to copy files from mulitple source
directories to a target directory while maintaining the subdirecotries.  Also, each file
will be transformed using [stencil](https://github.com/davidsantiago/stencil).  The map 
that is passed to stencil contains a combination of:

* The project map
* The system properties (with .prop added to the name )
* Additional values (currently only :timestamp)
* Values set in the project.clj using :stencil :extra-values

## Usage

To use from Leiningen add to `project.clj`:

      :plugins [ [lein-stencil "0.1.0"] ]

To have it run before the jar file creation:

      :prep-tasks ["javac" "compile" "stencil"]

To configure lein-stencil, add to `project.clj`

    :stencil {
        :resource-paths ["src-stencil"] ;; required or does nothing
        :target-path "target/html"      ;; optional default to the global one
        :extra-values { :year ~(.get (java.util.GregorianCalendar.)
                                         (java.util.Calendar/YEAR)) }  ;; optional - default to nil

If `:resource-paths` is not set or is nil, then it won't do anything

To see all the properties that are passed to stencil:

    lein stencil pprint

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


## License

Copyright (C) 2012 Matther O. Smith

Distributed under the Eclipse Public License, the same as Clojure.
