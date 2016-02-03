(ns kbd.recorded-performance
  (require [overtone.core :as o]
           [overtone.music.time :refer [now player-pool]]
           [overtone.at-at :as at-at]))

(defn read-performance [filename]
  (read-string (slurp filename)))

;(in-ns 'kbd.recorded-performance)
(defn translate-performance
  ([performance]
    (translate-performance performance (+ 1000 (now))))
  ([performance begin-time]
    (let [d 1000
          start-msg (first performance)
          timestamp (/ (:timestamp start-msg) d)
          offset (- (+ 1000 begin-time) timestamp)
          translator (fn [t] (+ offset (/ t d)))]
      (map #(update % :timestamp translator) performance))))

(defn play-at [t fun]
  (println "\nplaying at " t " and now is " (now) " so playing in " (- t (now)))
  (at-at/at t fun player-pool :desc "performance"))

(defn play-performance [performance {:keys [play-note cease]}]
  (let [on-notes (atom {})]
    (letfn [(play-event [{:keys [command note velocity] :as evt}]
              (case command
                :channel-pressure nil
                :program-change (o/stop)
                :control-change nil
                :note-on (let [token (play-note note velocity)]
                           (println "\npp token: "token)
                           (swap! on-notes #(assoc % note token))
                           (println "\nkeys@on " (keys @on-notes)))
                :note-off (do (when-let [token (@on-notes note)]
                                (cease token)
                                (swap! on-notes #(dissoc % note)))
                              (println "\nkeys@off " (keys @on-notes)))))
            (play-next [the-perf]
              (let [evt (first the-perf)
                    next-perf (next the-perf)]         
                       (println "t - now = " (- (:timestamp evt) (now)))
              (play-at (:timestamp evt)
                (fn []
                  (try
                    (println "playing " evt)
                    (println "soon will play:\n" (count next-perf))
                    (play-event evt)
                    (when next-perf
                      (play-next next-perf))
                    (catch Exception e
                      (println "\n\n\nexception:")
                      (println e)))))))]
      (try
        (play-next (translate-performance performance))
        (catch Exception e
          (println "\n\n\nexception:")
          (println e))))))
