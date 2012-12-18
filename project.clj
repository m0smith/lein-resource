(defproject lein-stencil "0.1.0"
  :description "lein-stencil: a lein plugin to copy resources and apply a stencil transformation on them"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [stencil "0.3.1"]]
  :plugins [[lein-pprint "1.1.1"]]
  :scm {:url "git@github.com:m0smith/lein-stencil.git"}
  :url "https://github.com/m0smith/lein-stencil"
  :eval-in-leiningen true
  :pom-addition [:developers [:developer
                              [:name "Matthew O. Smith"]
                              [:url "http://m0smith.com"]
                              [:email "matt@m0smith.com"]
                              [:timezone "-7"]]]))