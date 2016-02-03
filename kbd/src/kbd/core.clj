(ns kbd.core
  (:require [seesaw.core :as sc]
            [seesaw.font :as sf]
            [overtone.core :as o :refer :all]
            [kbd.recorded-performance :as perf])
  (:import [java.awt.event ActionListener KeyListener KeyEvent])
  (:use [overtone.synth.sts :only [prophet]]))

;(o/boot-external-server)
(o/connect-external-server 57110)  ; scsynth.exe -u 57110 -H "ASIO : ASIO4ALL v2"

(println "volume: " (overtone.studio.mixer/volume))
;(overtone.studio.mixer/volume 0.01)

;; TB-303 clone Supercollider starting point by Dan Stowell
;; http://permalink.gmane.org/gmane.comp.audio.supercollider.user/22591
;; SynthDef("sc303", { arg out=0, freq=440, wave=0, ctf=100, res=0.2,
;;    sus=0, dec=1.0, env=1000, gate=0, vol=0.2;
;;  var filEnv, volEnv, waves;
;;
;;  // can't use adsr with exp curve???
;;  //volEnv = EnvGen.ar(Env.adsr(1, 0, 1, dec, vol, 'exp'), In.kr(bus));
;;  volEnv = EnvGen.ar(Env.new([10e-10, 1, 1, 10e-10], [0.01, sus, dec],
;;                                  'exp'), gate);
;;  filEnv = EnvGen.ar(Env.new([10e-10, 1, 10e-10], [0.01, dec],
;;                                  'exp'), gate);
;;
;;  waves = [Saw.ar(freq, volEnv), Pulse.ar(freq, 0.5, volEnv)];
;;
;;  Out.ar(out, RLPF.ar( Select.ar(wave, waves), ctf + (filEnv * env), res).dup * vol);
;; }).send(s);
;; Overtone port by Roger Allen.
; (o/defsynth tb-303
;   "A clone of the sound of a Roland TB-303 bass synthesizer."
;   [note     30        ; midi note value input
;    wave     0         ; 0=saw, 1=square
;    cutoff   100       ; bottom rlpf frequency
;    env      1000      ; + cutoff is top of rlpf frequency
;    res      0.2       ; rlpf resonance
;    sus      0         ; sustain level
;    dec      1.0       ; decay
;    amp      1.0       ; output amplitude
;    gate     0         ; on/off control
;    action   o/NO-ACTION ; keep or FREE the synth when done playing
;    position 0         ; position in stereo field
;    out-bus  0]
;   (let [freq-val   (o/midicps note)
;         amp-env    (o/env-gen (o/envelope [10e-10, 1, 1, 10e-10]
;                                           [0.01, sus, dec]
;                                           :exp)
;                               :gate gate :action action)
;         filter-env (o/env-gen (o/envelope [10e-10, 1, 10e-10]
;                                           [0.01, dec]
;                                           :exp)
;                               :gate gate :action action)
;         waves      [(* (o/saw freq-val) amp-env)
;                     (* (o/pulse freq-val 0.5) amp-env)]
;         tb303      (o/rlpf (o/select wave waves)
;                            (+ cutoff (* filter-env env)) res)]
;     (o/out out-bus (* amp (o/pan2 tb303 position)))))

;; translated from: https://github.com/supercollider-quarks/SynthDefPool/blob/master/pool/apad_mh.scd
(definst simple-flute [freq 880
                       amp 0.5
                       attack 0.4
                       decay 0.5
                       sustain 0.8
                       release 1
                       gate 1
                       out 0]
  (let [env  (env-gen (adsr attack decay sustain release) gate :action FREE)
        mod1 (lin-lin:kr (sin-osc:kr 6) -1 1 (* freq 0.99) (* freq 1.01))
        mod2 (lin-lin:kr (lf-noise2:kr 1) -1 1 0.2 1)
        mod3 (lin-lin:kr (sin-osc:kr (ranged-rand 4 6)) -1 1 0.5 1)
        sig (distort (* env (sin-osc [freq mod1])))
        sig (* amp sig mod2 mod3)]
    sig))

(definst rise-fall-pad
  [freq 440 t 4 amt 0.3 amp 0.8]
  (let [f-env      (env-gen (perc t t) 1 1 0 1 FREE)
        src        (saw [freq (* freq 1.01)])
        signal     (rlpf (* 0.3 src)
                         (+ (* 0.6 freq) (* f-env 2 freq)) 0.2)
        k          (/ (* 2 amt) (- 1 amt))
        distort    (/ (* (+ 1 k) signal) (+ 1 (* k (abs signal))))
        gate       (pulse (* 2 (+ 1 (sin-osc:kr 0.05))))
        compressor (compander distort gate 0.01 1 0.5 0.01 0.01)
        dampener   (+ 1 (* 0.5 (sin-osc:kr 0.5)))
        reverb     (free-verb compressor 0.5 0.5 dampener)
        echo       (comb-n reverb 0.4 0.3 0.5)]
    (* amp echo)))

(def char->note-string
  {\Q "C4"
   \2 "C#4"
   \W "D4"
   \3 "D#4"
   \E "E4"
   \R "F4"
   \5 "F#4"
   \T "G4"
   \6 "G#4"
   \Y "A4"
   \7 "A#4"
   \U "B4"
   \I "C5"
   \9 "C#5"
   \O "D5"
   \0 "D#5"
   \P "E5"
   \[ "F5"
   \= "F#5"
   \] "G5"
   \\ "A5"})

(defn KeyEvent->note [^KeyEvent ke]
  (when-let [note-string (-> (.getKeyCode ke) char char->note-string)]
    (o/note note-string)))

(o/definst baz [freq 440]
  (* 0.3 (o/saw freq)))

(def f (sc/frame :title "my app" :on-close :exit))

(def ^:dynamic *inst* simple-flute)
(def ^:dynamic *note-shift* 0)

(def on-notes (atom {}))
(def long-on-notes (atom {}))

(def note-maps
  {nil on-notes
   :long long-on-notes})
(def instruments
  (atom
    {nil simple-flute
     :long simple-flute}))

(def note-shifts
  (atom
    {:long -12}))

(defn play-note
  ([note] (play-note note 127))
  ([note vel]
   (let [transposed-note (+ note *note-shift*)
        hz (o/midi->hz transposed-note)]
    (println "\ntransposed-note " transposed-note " x " vel " @ " hz " hz on " (:name *inst*))
    (*inst* hz))))

(defn cease [token]
  (println "\nceasing " token)
  (with-inactive-node-modification-error :silent
    (node-control token [:gate 0 :after-touch 0.0])))

(defn note-on
  ([note]
    (note-on note nil))
  ([note bank-key]
    (let [note-map (note-maps bank-key)]
      (when-not (@note-map note)
      (swap! note-map assoc note 
             (binding [*inst* (@instruments bank-key)
                       *note-shift* (@note-shifts bank-key 0)]
                (when *inst*
                  (play-note note))))))))

(defn note-off [note bank-key]
  (let [note-map (note-maps bank-key)]
    (when-let [token (@note-map note)]
      (cease token))
    (swap! note-map dissoc note)))

; (def receiver
;   (let [receivers (o/midi-connected-receivers)]
;     (or (first (filter #(= (:name %) "Yamaha MOTIF ES-8") receivers))
;       (first receivers))))

; (println receiver)

; (defn play-note
;   ([note] (play-note note 127))
;   ([note vel]
;     (let [ports [0 4]
;           tokens (map (partial assoc {:note note :vel (or vel 127)} :port) ports)]
;       (doseq [{:keys [note port vel]} tokens]
;         (overtone.midi/midi-note-on receiver note (or vel 127) port))
;       tokens)))
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

(defn KeyEvent->bank-key [^KeyEvent e]
  (when (.isShiftDown e) :long))

(defn input-listener []
  (proxy [ActionListener KeyListener] []
    (actionPerformed [e])
    (keyPressed [e]
      (when-let [note (KeyEvent->note e)]
        (note-on note (KeyEvent->bank-key e)))
    #_(println "You pressed " (.getKeyChar e)))
    (keyReleased [e]
       #_(println e "You released a key!")
       (let [k (.getKeyChar e)
             note (KeyEvent->note e)]
         (note-off note (KeyEvent->bank-key e))))
    (keyTyped [e])))

(doto f
  (.addKeyListener (input-listener)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!")
  (sc/show! f))

#_
;(in-ns 'kbd.core)
(let [performance (perf/read-performance "..\\performance\\sat.edn")
      part (->> performance (drop 5) #_(take 20) vec)]
    ;(pprint (map #(select-keys % [:note :command]) part))
  (perf/play-performance part {:play-note play-note :cease cease}))
