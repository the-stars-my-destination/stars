(ns cassiopeia.destination.ruchbah
  "
 ######
 #     # #    #  ####  #    # #####    ##   #    #
 #     # #    # #    # #    # #    #  #  #  #    #
 ######  #    # #      ###### #####  #    # ######
 #   #   #    # #      #    # #    # ###### #    #
 #    #  #    # #    # #    # #    # #    # #    #
 #     #  ####   ####  #    # #####  #    # #    #

 An Algol-type eclipsing variable star. It appears to have a blue-white hue and it is 99 light-years from Earth.
"
  (:use overtone.live)
  (:use cassiopeia.samples)
  (:use cassiopeia.warm-up)
  (:require [cassiopeia.engine.timing :as timing]
            [overtone.inst.synth :as s]
            [cassiopeia.engine.sequencer :as sequencer]))

(defsynth deep-saw [freq 100 beat-count-bus 0 offset-bus 0 duration-bus 0 out-bus 0 amp 1 pan 0 room 0.5 damp 0.5]
  (let [cnt    (in:kr beat-count-bus)
        offset (buf-rd:kr 1 offset-bus cnt)
        durs   (buf-rd:kr 1 duration-bus cnt)
        trig (t-duty:kr (dseq durs INFINITE))
        freq (demand:kr trig 0 (drand offset INFINITE))
        freq (midicps freq)

        saw1 (lf-saw:ar (* 0.5 freq))
        saw2 (lf-saw:ar (* 0.25 freq))
        sin1 (sin-osc freq)
        sin2 (sin-osc (* 1.01 freq))
        src (mix [saw1 saw2 sin1 sin2])
        env (env-gen:ar (env-asr) trig)
        src (lpf:ar src)
        src (free-verb :in src :mix 0.33 :room room :damp damp)]
    (out out-bus (pan2 (* amp [src src]) pan))))

(defsynth melody [duration-bus 0 room 0.5 damp 0.5 beat-count-bus 0 offset-bus 0 amp 1 out-bus 0]
  (let [cnt    (in:kr beat-count-bus)
        offset (buf-rd:kr 1 offset-bus cnt)
        durs   (buf-rd:kr 1 duration-bus cnt)
        trig (t-duty:kr (dseq durs INFINITE))
        freq (demand:kr trig 0 (drand offset INFINITE))
        freq (midicps freq)

        env (env-gen:ar (env-asr :release 0.25 :sustain 0.8) trig)
        src (* 0.3 (lf-tri:ar freq))
        src (pitch-shift  src 0.1 0.9 0 0)
        ]
    (out:ar out-bus (* amp env (pan2 src (t-rand:kr -1 1 trig))))))

(comment
  (melody))

(def flow-buf (buffer 128))

(do
  (definst overpad
    [amp 0.7 attack 0.001 release 2 note-buf 0 beat-count-bus 0 beat-trg-bus 0
     tonal 0.99 bass-thrust 0.7 fizzing 3]
    (let [cnt  (in:kr beat-count-bus)
          trg  (in:kr beat-trg-bus)
          note (buf-rd:kr 1 note-buf cnt)

          freq  (midicps note)
          env   (env-gen (perc attack release) trg)
          f-env (+ freq (* fizzing freq (env-gen (perc 0.012 (- release 0.1)))))
          bfreq (/ freq 2)
          sig   (apply +
                       (concat (* bass-thrust (sin-osc [bfreq (* tonal bfreq)]))
                               (lpf (saw [freq (* freq 1.01)]) f-env)))
          audio (* amp env sig)]
      audio))

  (kill overpad)

  (def o (overpad :release 16
                  :note-buf flow-buf
                  :beat-count-bus (:count timing/beat-2th)
                  :beat-trig-bus (:beat timing/beat-2th)
                  :amp 0.2
                  :attack 5)))
(stop)

(definst tb303
  [note-buf 0
   beat-count-bus 0
   beat-trg-bus 0
   wave       {:default 1 :min 0 :max 2 :step 1}
   r          {:default 0.8 :min 0.01 :max 0.99 :step 0.01}
   attack     {:default 0.01 :min 0.001 :max 4 :step 0.001}
   decay      {:default 0.1 :min 0.001 :max 4 :step 0.001}
   sustain    {:default 0.6 :min 0.001 :max 0.99 :step 0.001}
   release    {:default 0.01 :min 0.001 :max 4 :step 0.001}
   cutoff     {:default 100 :min 1 :max 20000 :step 1}
   env-amount {:default 0.01 :min 0.001 :max 4 :step 0.001}
   amp        {:default 0.5 :min 0 :max 15 :step 0.01}]
  (let [cnt (in:kr beat-count-bus)
        note (buf-rd:kr 1 note-buf cnt)
        trg  (in:kr beat-trg-bus)

        freq       (midicps note)
        freqs      [freq (* 1.01 freq)]
        vol-env    (env-gen (adsr attack decay sustain release) :gate trg)
        fil-env    (env-gen (perc) :gate trg)
        fil-cutoff (+ cutoff (* env-amount fil-env))
        waves      (* vol-env
                      [(saw freqs)
                       (pulse freqs 0.5)
                       (lf-tri freqs)])
        selector   (select wave waves)
        filt       (rlpf selector fil-cutoff r)]
    (* amp 100 filt)))

(def tb (tb303 :attack 4
               :amp 0
               :sustain 0.99
               :release 4
               :decay 4
               :note-buf flow-buf
               :beat-count-bus (:count timing/beat-2th)
               :beat-trg-bus   (:beat timing/beat-2th)))

(ctl tb :amp 1)
(ctl tb :env-amount 10 :waves 3 :sustain 0 :release 1)
(ctl tb :env-amount 0.01 :attack 5 :waves 3 :sustain 0.6 :release 16)

(comment
  (kill tb))

(def moo (s/mooger (note :A3)))
(ctl moo :note (note :A2) :attack 16)

(def o (overpad :release 16
                :note-buf flow-buf
                :beat-count-bus (:count timing/beat-2th)
                :beat-trig-bus (:beat timing/beat-2th)
                :amp 0
                :attack 5))

(ctl o :amp 0.2)
(ctl o :attack 0 :release 1)

(ctl o :fizzing 3)
(ctl o :bass-thrust 2)
(ctl o :tonal 4)



(kill overpad)

(buffer-write! flow-buf        (take 128 (cycle (map note [:A3 :E3 :D3 :C4  :A3 :E3 :D3 :C3]))))

(defonce bass-duration-b (buffer 128))
(defonce bass-notes-b    (buffer 128))

(def deep (deep-saw :duration-bus bass-duration-b :offset-bus bass-notes-b
                    :beat-count-bus (:count timing/beat-2th) :amp 0))

(buffer-write! bass-duration-b (take 128 (cycle           [1 1 1 1])))
(buffer-write! bass-notes-b    (take 128 (cycle (map note [:A3 :E3 :D3 :C4  :A3 :E3 :D3 :C3]))))

(buffer-write! bass-duration-b (take 128 (cycle           [1/4])))
(buffer-write! bass-notes-b    (take 128 (cycle (map note [:E2 :D2 :B2]))))

(defonce melody-duration-b (buffer 128))
(defonce melody-notes-b    (buffer 128))

(def m (melody :duration-bus melody-duration-b :offset-bus melody-notes-b
               :beat-count-bus (:count timing/beat-1th) :amp 0))

(ctl m :amp 0.9)

(comment (kill m))

(buffer-write! melody-duration-b (take 128 (cycle [1/2 1/4 1/4 1/2])))
(buffer-write! melody-notes-b (take 128 (cycle (map note [:A3 :A4 :B4 :C4]))))
(buffer-write! melody-duration-b (take 128 (cycle [1/8 1/4 1/8 1/4
                                                   1/8 1/8 1/4 1/8])))

(buffer-write! melody-notes-b (take 128 (cycle (map #(+ 0 (note %)) [:A3 :A4 :B4 :C4
                                                                     :c4 :B4 :A4 :A3]))))

(kill s/mooger)
(s/cs80lead :freq 90 :amp 0.2)
(kill s/cs80lead)

(s/overpad :note 60 :release 16)

(s/rise-fall-pad :freq (midi->hz (note :A3)))
(ctl deep :amp 0.5)

(kill s/rise-fall-pad)

(def bass-notes-buf (buffer 8))
(def phase-bass-buf (buffer 8))

(definst vintage-bass
  [velocity 80 t 0.6 amp 1 seq-buf 0 note-buf 0 beat-trg-bus 0 beat-bus 0 num-steps 8 beat-num 0]
  (let [cnt      (in:kr beat-bus)
        beat-trg (in:kr beat-trg-bus)
        note     (buf-rd:kr 1 note-buf cnt)
        bar-trg (and (buf-rd:kr 1 seq-buf cnt)
                     (= beat-num (mod cnt num-steps))
                     beat-trg)

        freq     (midicps note)
        sub-freq (midicps (- note 12))
        velocity (/ velocity 127.0)
        sawz1    (* 0.275 (saw [freq (* 1.01 freq)]))
        sawz2    (* 0.75 (saw [(- freq 2) (+ 1 freq)]))
        sqz      (* 0.3 (pulse [sub-freq (- sub-freq 1)]))
        mixed    (* 5 (+ sawz1 sawz2 sqz))
        env      (env-gen (adsr 0.1 3.3 0.4 0.8) :gate bar-trg)
        filt     (*  (moog-ff mixed (* velocity (+ freq 200)) 2.2 bar-trg))]
        (* amp env filt)))

(doseq [i (range 0 9)]
  (vintage-bass :amp 0.4 :note-buf bass-notes-buf
                :seq-buf phase-bass-buf
                :beat-bus (:count timing/beat-1th)
                :beat-trg-bus (:beat timing/beat-1th) :num-steps 8 :beat-num i))

(ctl timing/root-s :rate 2)

(kill vintage-bass)

(buffer-write! bass-notes-buf  (take 8 (cycle (map note [:E2]))))
(buffer-write! bass-notes-buf  (take 8 (cycle (map note [:D3]))))
(buffer-write! phase-bass-buf  [1 0 1 0 1 0 1 0])

(def beats-g (group "beats"))

(def dum-samples-set [kick-s clap2-s snare-s hip-hop-kick-s sizzling-high-hat-s])
(sequencer/swap-samples! sequencer-64 dum-samples-set)

;;(sequencer/sequencer-write! sequencer-64 0 [1 0 1 0 1 1 0 0])
;;(sequencer/sequencer-write! sequencer-64 0 [0 0 0 0 0 1 1 0])

(sequencer/sequencer-write! sequencer-64 1 [0 0 0 0 0 0 0 1])
(sequencer/sequencer-write! sequencer-64 2 [0 1 0 0 0 0 1 0])
(sequencer/sequencer-write! sequencer-64 3 [1 1 1 1 1 1 1 1])
(sequencer/sequencer-write! sequencer-64 3 [0 0 0 0 0 0 0 0])
(sequencer/sequencer-write! sequencer-64 4 [0 0 0 0 0 0 0 1])

(sequencer/sequencer-write! sequencer-64 1 [0 0 0 0 0 0 0 0])
(sequencer/sequencer-write! sequencer-64 2 [0 0 0 0 0 0 0 0])
(sequencer/sequencer-write! sequencer-64 3 [0 0 0 0 0 0 0 0])
(sequencer/sequencer-write! sequencer-64 3 [0 0 0 0 0 0 0 0])
(sequencer/sequencer-write! sequencer-64 4 [0 0 0 0 0 0 0 0])


(ctl timing/root-s :rate 2)

(defsynth bazz [beat-bus 0 beat-trg-bus 0 note-buf 0 seq-buf 0 beat-num 0 num-steps 0]
  (let [cnt      (in:kr beat-bus)
        beat-trg (in:kr beat-trg-bus)
        note     (buf-rd:kr 1 note-buf cnt)
        bar-trg (and (buf-rd:kr 1 seq-buf cnt)
                     (= beat-num (mod cnt num-steps))
                     beat-trg)

        freq (t-rand 50 1300 bar-trg)
        c (pm-osc:ar freq (* freq (t-rand 0.25 2.0 bar-trg)) (t-rand 0.1 (* 2 Math/PI) bar-trg))
        e (env-gen:kr (env-perc 0.001 0.1) bar-trg)]
    (out [0 1] (/ (* c e 0.125 ) 2))))

(bazz)

(defsynth flek []
  (let [freq 550
        e (env-gen:kr (env-perc 0.001 4.0) :action FREE)
        c [(pulse:ar (+ (ranged-rand 1.0 10.0) (* freq (+ 0 1))) (lfd-noise1 (ranged-rand 1 10)))
           (pulse:ar (+ (ranged-rand 1.0 10.0) (* freq (+ 1 1))) (lfd-noise1 (ranged-rand 1 10)))
           (pulse:ar (+ (ranged-rand 1.0 10.0) (* freq (+ 2 1))) (lfd-noise1 (ranged-rand 1 10)))]]
    (out [0 1 20] (/ (* c e 0.125) 2))))

(flek)

(doseq [i (range 0 9)]
  (bazz :amp 0.4 :note-buf bass-notes-buf
        :seq-buf phase-bass-buf
        :beat-bus (:count timing/beat-1th)
        :beat-trg-bus (:beat timing/beat-1th) :num-steps 8 :beat-num i))

(kill bazz)


(definst v-bass
  [velocity 80 t 0.6 amp 1 seq-buf 0 note-buf 0 beat-trg-bus 0 beat-bus 0 num-steps 8 beat-num 0]
  (let [cnt      (in:kr beat-bus)
        beat-trg (in:kr beat-trg-bus)
        note     (buf-rd:kr 1 note-buf cnt)
        bar-trg (and (buf-rd:kr 1 seq-buf cnt)
                     (= beat-num (mod cnt num-steps))
                     beat-trg)

        freq     (midicps note)
        sub-freq (midicps (- note 12))
        velocity (/ velocity 127.0)
        sawz1    (* 0.275 (saw [freq (* 1.01 freq)]))
        sawz2    (* 0.75 (saw [(- freq 2) (+ 1 freq)]))
        sqz      (* 0.3 (pulse [sub-freq (- sub-freq 1)]))
        mixed    (* 5 (+ sawz1 sawz2 sqz))
        env      (env-gen (adsr 0.1 3.3 0.4 0.8) :gate bar-trg)
        filt     (*  (moog-ff mixed (* velocity (+ freq 200)) 2.2 bar-trg))]
        (* amp env filt)))


(defsynth r-kick [out-bus 0 beat-bus 0 beat-trg-bus 0 note-buf 0 num-steps 8 seq-buf 0 beat-num 0]
  (let [cnt      (in:kr beat-bus)
        beat-trg (in:kr beat-trg-bus)
        note     (buf-rd:kr 1 note-buf cnt)
        bar-trg (and (buf-rd:kr 1 seq-buf cnt)
                     (= beat-num (mod cnt num-steps))
                     beat-trg)

        e (env-gen (env-perc 0.001 2.0) :gate bar-trg)
        c (+ (* e 0.3 (sin-osc:ar (line:kr 100 10 0.1)))
             (env-gen:ar (* (line:kr 0.05 0 0.001) (clip-noise:ar)) :gate bar-trg ))]
    (out [0 1] (* e c))))

(require '[overtone.inst.drum :as d])

(def v-bass-buf (buffer 128))

(definst dub-kick
  [freq 80
   beat-bus 0 beat-trg-bus 0 note-buf 0 num-steps 8 seq-buf 0 beat-num 0 ]
  (let [cnt      (in:kr beat-bus)
        beat-trg (in:kr beat-trg-bus)
        note     (buf-rd:kr 1 note-buf cnt)
        bar-trg (and (buf-rd:kr 1 seq-buf cnt)
                     (= beat-num (mod cnt num-steps))
                     beat-trg)

        cutoff-env (perc 0.001 1 freq -20)
        amp-env (perc 0.001 1 1 -8)
        osc-env (perc 0.001 1 freq -8)
        noiz (lpf (white-noise) (+ (env-gen:kr cutoff-env :gate bar-trg) 20))
        snd  (lpf (sin-osc (+ (env-gen:kr osc-env :gate bar-trg) 20)) 200)
        mixed (* (+ noiz snd) (env-gen amp-env :gate bar-trg))]
    mixed))

(doseq [i (range 0 18)]
  (dub-kick :amp 0.4 :note-buf bass-notes-buf
          :seq-buf v-bass-buf
          :beat-bus (:count timing/beat-1th)
          :beat-trg-bus (:beat timing/beat-1th) :num-steps 18 :beat-num i))

(ctl timing/root-s :rate 2)

(kill dub-kick)

(buffer-write! v-bass-buf  (take 128 (cycle [1 0 0 1 1 0 0 1 0 0 1 0 0])))
(buffer-write! v-bass-buf  (take 128 (cycle [1 0 0 1 1 0 1 0 0 1 0 0 1 0 0 1 0 0 1 0 0 1 0 0])))
(buffer-write! v-bass-buf  (take 128 (cycle [1 0 0 1 1 0 1 1 0 0 1 1 0 0 1 1 0 0 1 1 0 0 1 1 0 0 1 1 0 0 1])))

(def o-buf (buffer 128))

(defonce melody-duration-b (buffer 128))
(defonce melody-notes-b    (buffer 128))

(def m (melody :duration-bus melody-duration-b :offset-bus melody-notes-b
               :beat-count-bus (:count timing/beat-1th) :amp 0))

(ctl m :amp 0.7)

(buffer-write! melody-duration-b (take 128 (cycle [1/2 1/4 1/128 1/2])))

;;(buffer-write! melody-duration-b (take 128 (cycle [1/2 1/2 1/2 1/2])))

(buffer-write! melody-notes-b (take 128 (cycle (map note [:A3 :A4 :B4 :C4]))))
(buffer-write! melody-notes-b (take 128 (cycle (map note [:A3 :A4 :B4 :C4 :D3 :D2 :B2 :D4]))))


(comment
  (stop))