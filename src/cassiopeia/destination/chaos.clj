(ns cassiopeia.destination.chaos
"
  ██████╗██╗  ██╗ █████╗  ██████╗ ███████╗
 ██╔════╝██║  ██║██╔══██╗██╔═══██╗██╔════╝
 ██║     ███████║███████║██║   ██║███████╗
 ██║     ██╔══██║██╔══██║██║   ██║╚════██║
 ╚██████╗██║  ██║██║  ██║╚██████╔╝███████║
  ╚═════╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝

* it must be sensitive to initial conditions
* it must be topologically mixing
* its periodic orbits must be dense."
  (:use [overtone.live]
        [cassiopeia.samples]
        [overtone.synth.sampled-piano]
        [cassiopeia.engine.buffers])
  (:require [cassiopeia.engine.timing :as timing]
            [launchpad.sequencer :as lp-sequencer]
            [cassiopeia.engine.mixers :as m]
            [overtone.inst.synth :as s]
            [overtone.synths :as syn]))

(defn fade
  "Fade amplitude out over time"
  [node start rate]
  (loop [vol start]
    (when (>= vol 0)
      (println vol)
      (Thread/sleep 200)
      (ctl node :amp vol)
      (recur (- vol rate)))))

(defn over-time
  "Over time change val of `field` to end"
  [node field start end rate]
  (loop [vol start]
    (when (>= vol end)
      (println vol)
      (Thread/sleep 200)
      (ctl node field vol)
      (recur (- vol rate)))))

;;;;;;;;;;;;;;;
;;Instruments;;
;;;;;;;;;;;;;;;
(do
  (defn sputter
    "returns a sequence of length maxlen with the items partly repeated (random choice of given probability)."
    ([list]          (sputter list 0.5 100 []))
    ([list prob]     (sputter list prob 100 []))
    ([list prob max] (sputter list prob max []))
    ([[head & tail] prob max-length result]
       (if (and head (< (count result) max-length))
         (if (< (rand) prob)
           (recur (cons head tail) prob max-length (conj result head))
           (recur tail prob max-length (conj result head)))
         result)))

  (def pattern (sputter (range 0 8) 0.8))
  (def degree-pattern (map #(nth (sort (keys DEGREE)) %) pattern))
  (def pitch-pattern (map #(if (nil? %) (note :G4) %) (degrees->pitches degree-pattern :diatonic :G4)))

  (def detune-buf (buffer (count pitch-pattern)))
  (def notes-buf  (buffer (count pitch-pattern)))

  (def space-and-time-sun (load-sample "~/Workspace/music/samples/chaos.wav"))

  (def chaos-s (load-sample "~/Workspace/music/samples/chaos.wav"))

  (def pinging (load-sample "~/Workspace/music/samples/pinging.wav"))

  (defsynth granulate [in-buf 0 amp 1]
    (let [trate (mouse-y:kr 2 120 1)
          b in-buf
          dur (/ 1.2 trate)
          src (t-grains:ar :num-channels 1
                           :trigger (impulse:ar trate)
                           :bufnum b
                           :rate 1
                           :center-pos (mouse-x:kr 0 (buf-dur b))
                           :dur dur
                           :pan (* 0.6 (white-noise:kr))
                           :amp amp)]
      (out 0 src)))

  (defsynth noisemator [in-bus 0 out-bus 0]
    (let [f (in:ar in-bus)]
      (out out-bus (lf-noise1:ar f))))

  (def main-b (audio-bus))

  (comment
    (sample-player space-and-time-sun :out-bus main-b :loop? 1))

  (defsynth playit [in-bus 0 out-bus 0 amp 1]
    (let [src (in:ar in-bus 1)
          n (lf-noise0)]
      (out [0 1] (* amp n src) )))

  (comment
    (def noisey  (playit :in-bus main-b))

    (ctl noisey :amp 0.9))

  (defsynth glitchift [amp 1 out-bus 0]
    (let [l (local-in:ar 2)
          k (+ 1 (* l (lf-saw:ar l 0)))
          j (range-lin k 0.25 4.0)
          s (pitch-shift:ar (sin-osc-fb (pow j [l k]) k) [0.05 0.03] j)]
      (local-out:ar s)
      (out out-bus (* amp (pan2 s)))))

  (defsynth drone [amp 1 out-bus 0 f1 1 f2 1]
    (let [l (local-in:ar 2)
          k (+ 1 (* l (lf-saw:ar l 0)))
          j (range-lin:ar 0.2 10.0)
          s (pitch-shift (sin-osc-fb:ar (pow j [f1 f2]) k) [0.05 0.03] j)]
      (local-out:ar s)
      (out out-bus (* amp (pan2 (free-verb s :room 1 :damp 1 :mix 1))))))

  (defsynth phasing-ping [out-bus 0 amp 0.1 gate 1]
    (let [cnt    (in:kr timing/beat-count-b)
          detune (buf-rd:kr 1 detune-buf cnt)
          note (buf-rd:kr 1 notes-buf cnt)
          freq (midicps note)

          trig (t-duty:kr (dseq [1/8] INFINITE))
          freqd (demand:kr trig 0 (drand freq INFINITE))

          env (env-gen:ar (env-perc (ranged-rand 0.001 0.01) (lin-rand 0.2 0.4) amp (ranged-rand -9 -1) FREE) trig)
          snd (mix (sin-osc:ar (+ freqd [0 (* 0.2 detune)]) (* 2 Math/PI env)))]
      (out out-bus (pan2:ar (* snd env amp) (t-rand:kr -1 1 trig)))))

  (defonce p-g (group "Group for p2s"))
  (defsynth p2 [out-bus 0 group-size 5 idx 0 amp 1]
    (let [b-trig (in:kr timing/beat-b)
          cnt (in:kr timing/beat-count-b)
          detune (buf-rd:kr 1 detune-buf cnt)
          note (buf-rd:kr 1 notes-buf cnt)
          freq (midicps note)

          trg (and b-trig (= 0 (mod (+ (rand-int 5) idx cnt) group-size)))
          freq (latch freq trg)

          env (env-gen (env-perc (ranged-rand 0.001 0.01) 0.4 amp (ranged-rand -9 -1)) trg)
          snd (* env (mix (sin-osc:ar (+ freq [0 (* 0.1 detune)]) (* 2 Math/PI env))))]

      (out out-bus (pan2:ar (* amp snd) (t-rand:kr -1 1 trg) ))))

  (def drum-buf (buffer 4))
  (def factor-buf (buffer 3))

  (defsynth drum-beat [amp 1 out-bus 0 speed 2 pop-speed 1 m 0 r 0 d 0]
    (let [cnt    (in:kr timing/beat-count-b)
          dur    (buf-rd:kr 1 drum-buf cnt)
          factor (buf-rd:kr 1 factor-buf cnt)

          trig (t-duty:ar 1 0 (* (dseq [dur]) [factor]))
          mod (* (+ 128 (* 32 (saw:ar 1))) (saw:ar [(* pop-speed 3) (* pop-speed 4)]))
          snd (/ (sin-osc:ar (+ 99 (* 64 (saw:ar speed))) mod) 9)
          src (comb-n snd 1/4 (/ 1 4.125) (range-lin (sin-osc:kr 0.005 (* 1.5 Math/PI)) 0 6))]
      (out out-bus (* amp (free-verb src m r d)))))

  (comment
    (def d (drum-beat))

    (ctl d :speed 0.1)
    (ctl d :pop-speed 0)

    (ctl d :d 0 :m 0 :r 0)
    (ctl d :d 1 :m 1 :r 0.5)

    (over-time d :d 1 0.1 0)

    (kill drum-beat)
    )

  (def count-buf (buffer 8))

  (defsynth flow [out-bus 0 cnt 1 amp 1 jump 99]
    (let [del (* 1 (delay-n:ar (in-feedback:ar 0 2) (in-feedback:ar 100 2) 1))
          src (/ (sin-osc:ar (+ (* cnt jump) [0 2]) (range-lin del 1 0)) 4)]
      (out out-bus (* amp (pan2 src) (line 1 0 16 FREE)))))

  (comment
    (flow :cnt 1 :jump (* 60))
    (kill flow)
    )

  (defsynth pluckey [out-bus 0 amp 1]
    (let [snd (pluck:ar (crackle:ar [1.9 1.8]) (mix (impulse:ar [(+ 1 (lin-rand 0 5)) (+ 1 (lin-rand 0 5))] -0.125)) 0.05 (lin-rand 0 0.05))
          src (bpf:ar snd (+ 100 (ranged-rand 0 2000) (lin-rand 0.25 1.75)))]
      (out 62 src)))

  (defsynth pluckey-wrapping [out-bus 0 amp 1]
    (let [src (* (in-feedback:ar 62 2) (range-lin (sin-osc:kr 0.006) 0.25 1))]
      (out out-bus (* amp src))))

  (defsynth noise-wind [out-bus 0 amp 1]
    (let [lfos (lf-noise1:ar 0.5)
          snd (crackle:ar (range-lin lfos 1.8 1.98))
          src (formlet:ar snd (lag (t-exp-rand:ar 200 2000 lfos) 2) (range-lin lfos 5e-4 1e-3) 0.0012)]
      (out out-bus (* amp src)))))

;;;;;;;;;
;;Score;;
;;;;;;;;;

(def grainy (granulate :in-buf (buffer-mix-to-mono chaos-s) :amp 0.5))
(kill grainy)

(def d (drone :amp 0.2))
(ctl d :f1 0.5 :amp 0.2)
(ctl d :f2 0.5 :amp 0.2)
(ctl d :f1 1)
(ctl d :f2 1)

(ctl d :f1 0.1)
(ctl d :f2 0.1)
(kill drone)

(fade d 0.2 0.01)

(def dark (syn/dark-sea-horns :amp 0.04))
(ctl dark :amp 0.01)
(fade dark 0.04 0.01)

(kill dark)

(def drum-t (drum-beat :amp 0.5))
(ctl drum-t :amp 0.0)

(ctl drum-t :speed 0)
(ctl drum-t :speed 1)
(ctl drum-t :speed 2)

(ctl drum-t :pop-speed 1)

(ctl drum-t :d 0 :m 0 :r 0)
(ctl drum-t :d 1 :m 1 :r 0.5)
(over-time drum-t :d 1 0.1 0)

(kill drum-beat)

(glitchift :amp 0.02)
(kill glitchift)

(do
  (pluckey)
  (pluckey-wrapping :amp 0.11))
(kill pluckey-wrapping)

(buffer-write! detune-buf (take (count pitch-pattern) (cycle [7])))
(buffer-write! notes-buf pitch-pattern)

(buffer-write! notes-buf (take (count pitch-pattern) (repeat (note :G4))))
(buffer-write! notes-buf (take (count pitch-pattern) (repeat (nth (distinct pitch-pattern) (mod 0 (count (distinct pitch-pattern)))))))

(dotimes [x 8] (p2 [:head p-g] 0 8 x))
(kill p-g)

(ctl timing/root-s :rate 100)

(phasing-ping :amp 0.4)
(kill phasing-ping)

(buffer-write! drum-buf [0 0 0 0])
(buffer-write! factor-buf [0 0 0])
(kill drum-beat)

(buffer-write! drum-buf [1 2 4 8])
(buffer-write! factor-buf [1 16 1])

(sample-player chaos-s :amp 0.5)
(sample-player pinging)

(def s (flow :cnt 1 :amp 0.5))
(def s (flow :cnt 2 :amp 0.4))
(def s (flow :cnt 3 :amp 0.3))
(def s (flow :cnt 4 :amp 0.2))
(def s (flow :cnt 5 :amp 0.5))
(def s (flow :cnt 6 :amp 0.5))
(def s (flow :cnt 7 :amp 0.5))

(kill flow)

(noise-wind :amp 0.1)
(kill noise-wind)

(stop)
