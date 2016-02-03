(defproject midi.performance "0.1.0-SNAPSHOT"
  :description "MIDI Performance Dashboard"
  :url "https://github.com/jeremyrsellars/midi.performance"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [seesaw "1.4.5"]
                 [overtone "0.9.1"]]
  :main ^:skip-aot midi.performance.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
