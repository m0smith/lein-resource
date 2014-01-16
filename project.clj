(defproject lein-resource "0.3.3"
  :description "lein-resource: a lein plugin to copy resources and apply a stencil transformation on them"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.0-RC1"]
                 [bultitude "0.2.4"]
                 [stencil "0.3.3"]]
  :plugins [[lein-pprint "1.1.1"]]
  :scm {:url "git@github.com:m0smith/lein-resource.git"}
  :url "https://github.com/m0smith/lein-resource"
  :eval-in-leiningen true
  :pom-addition [:developers [:developer
                              [:name "Matthew O. Smith"]
                              [:url "http://m0smith.com"]
                              [:email "matt@m0smith.com"]
                              [:timezone "-7"]]])
