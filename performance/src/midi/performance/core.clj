;scsynth.exe -u 57110 -H "ASIO : ASIO4ALL v2"
(ns midi.performance.core
  (:require [seesaw.core :as sc]
            [seesaw.font :as sf]
            [overtone.core :as o :refer :all])
  (:import [java.awt.event ActionListener KeyListener KeyEvent])
  ; (:use overtone.live)
  (:use [overtone.synth.sts :only [prophet]])
  #_(:use [overtone.synth.retro :only [tb-303]])
)

;(o/boot-external-server)
(o/connect-external-server 57110)  ; scsynth.exe -u 57110 -H "ASIO : ASIO4ALL v2"

(println "volume: " (overtone.studio.mixer/volume))
;(overtone.studio.mixer/volume 0.01)

(def midi-events (atom []))

(def f (sc/frame :title "Performance" :on-close :exit))

(def device
  (let [devices (o/midi-connected-devices)]
    (or (first (filter #(= (:name %) "Yamaha MOTIF ES-8") devices))
      (first devices))))

(println (clojure.pprint/pprint device))

(on-event [:midi :note-on]
          (fn [e]
            (let [note (:note e)
                  vel  (:velocity e)]
              (.setTitle f (str e))
              (println (clojure.pprint/pprint e))))
          ::note-printer-handler)

(defn add-event [evt]
  (let [e (dissoc evt :device :dev-key :msg)]
    (swap! midi-events #(conj % e))))

(on-event [:midi :channel-pressure] add-event ::channel-pressure-handler)
(on-event [:midi :control-change] add-event ::control-change-handler)
(on-event [:midi :note-off] add-event ::note-off-handler)
(on-event [:midi :note-on] add-event ::note-on-handler)
(on-event [:midi :pitch-bend] add-event ::pitch-bend-handler)
(on-event [:midi :poly-pressure] add-event ::poly-pressure-handler)
(on-event [:midi :program-change] add-event ::program-change-handler)

(defn unload-to-file [f]
  (let [events @midi-events]
    (reset! midi-events [])
    (spit f events)))
(unload-to-file "junk.edn")

; (defn play-note [note]
;   (let [ports [0 5]
;         tokens (map (partial assoc {:note note :vel 80} :port) ports)]
;     (doseq [{:keys [note port vel]} tokens]
;       (overtone.midi/midi-note-on receiver note (or vel 127) port))
;    tokens))
; (defn cease [tokens]
;   (doseq [{:keys [note port]} tokens]
;     (overtone.midi/midi-note-off receiver note port)))

; (doseq [v (range 16)]
;   (overtone.midi/midi-control receiver 32 (inc v) v))

; (println "javax.sound.midi.ShortMessage/PROGRAM_CHANGE  " javax.sound.midi.ShortMessage/PROGRAM_CHANGE)

; (overtone.midi/midi-control receiver 25 0)

; (note-on (o/note "C4"))
; (note-on (o/note "E4"))
; (note-on (o/note "G4"))

; (defn KeyEvent->bank-key [^KeyEvent e]
;   (when (.isShiftDown e) :long))

; (defn input-listener []
;   (proxy [ActionListener KeyListener] []
;     (actionPerformed [e])
;     (keyPressed [e]
;       (when-let [note (KeyEvent->note e)]
;         (note-on note (KeyEvent->bank-key e)))
;     #_(println "You pressed " (.getKeyChar e)))
;     (keyReleased [e]
;        #_(println e "You released a key!")
;        (let [k (.getKeyChar e)
;              note (KeyEvent->note e)]
;          (note-off note (KeyEvent->bank-key e))))
;     (keyTyped [e])))

; (doto f
;   (.addKeyListener (input-listener)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!")
  (sc/show! f)
  (println (.getTitle f)))
; (-main)