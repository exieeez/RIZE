import numpy as np
import wave
import struct

SAMPLE_RATE = 44100
DURATION = 20.0  # seconds
TOTAL_SAMPLES = int(SAMPLE_RATE * DURATION)
BPM = 120.0
BEAT_DUR = 60.0 / BPM  # 0.5 sec
BAR_DUR = BEAT_DUR * 4.0  # 2.0 sec

t = np.linspace(0, DURATION, TOTAL_SAMPLES, endpoint=False)
left = np.zeros(TOTAL_SAMPLES)
right = np.zeros(TOTAL_SAMPLES)

def add_tone(freq, start_t, dur, amp=0.3, wave_type='sine', pan=0.0):
    start_idx = int(start_t * SAMPLE_RATE)
    end_idx = min(TOTAL_SAMPLES, int((start_t + dur) * SAMPLE_RATE))
    if start_idx >= TOTAL_SAMPLES or end_idx <= start_idx:
        return
    n = end_idx - start_idx
    tn = np.linspace(0, dur, n, endpoint=False)
    
    # Envelope: attack + decay
    attack = min(0.015, dur * 0.1)
    release = min(0.05, dur * 0.3)
    env = np.ones(n)
    att_samples = int(attack * SAMPLE_RATE)
    rel_samples = int(release * SAMPLE_RATE)
    if att_samples > 0:
        env[:att_samples] = np.linspace(0, 1, att_samples)
    if rel_samples > 0:
        env[-rel_samples:] = np.linspace(1, 0, rel_samples)
        
    if wave_type == 'sine':
        sig = np.sin(2 * np.pi * freq * tn)
    elif wave_type == 'saw':
        sig = 2.0 * (tn * freq - np.floor(0.5 + tn * freq))
    elif wave_type == 'square':
        sig = np.sign(np.sin(2 * np.pi * freq * tn))
    elif wave_type == 'triangle':
        sig = 2.0 * np.abs(2.0 * (tn * freq - np.floor(tn * freq + 0.5))) - 1.0
    else:
        sig = np.sin(2 * np.pi * freq * tn)
        
    sig = sig * env * amp
    l_gain = np.sqrt(0.5 * (1.0 - pan))
    r_gain = np.sqrt(0.5 * (1.0 + pan))
    left[start_idx:end_idx] += sig * l_gain
    right[start_idx:end_idx] += sig * r_gain

def add_noise(start_t, dur, amp=0.1, pan=0.0):
    start_idx = int(start_t * SAMPLE_RATE)
    end_idx = min(TOTAL_SAMPLES, int((start_t + dur) * SAMPLE_RATE))
    n = end_idx - start_idx
    if n <= 0: return
    env = np.exp(-np.linspace(0, 8, n))
    noise = (np.random.rand(n) * 2 - 1) * env * amp
    l_gain = np.sqrt(0.5 * (1.0 - pan))
    r_gain = np.sqrt(0.5 * (1.0 + pan))
    left[start_idx:end_idx] += noise * l_gain
    right[start_idx:end_idx] += noise * r_gain

def add_kick(start_t, amp=0.7):
    start_idx = int(start_t * SAMPLE_RATE)
    dur = 0.25
    end_idx = min(TOTAL_SAMPLES, int((start_t + dur) * SAMPLE_RATE))
    n = end_idx - start_idx
    if n <= 0: return
    tn = np.linspace(0, dur, n, endpoint=False)
    freq = 140.0 * np.exp(-tn * 25.0) + 42.0
    phase = 2 * np.pi * np.cumsum(freq) / SAMPLE_RATE
    env = np.exp(-tn * 14.0)
    sig = np.sin(phase) * env * amp
    # add click
    click_n = min(n, int(0.005 * SAMPLE_RATE))
    sig[:click_n] += (np.random.rand(click_n) * 2 - 1) * 0.3
    left[start_idx:end_idx] += sig * 0.7
    right[start_idx:end_idx] += sig * 0.7

def add_snare(start_t, amp=0.5):
    start_idx = int(start_t * SAMPLE_RATE)
    dur = 0.22
    end_idx = min(TOTAL_SAMPLES, int((start_t + dur) * SAMPLE_RATE))
    n = end_idx - start_idx
    if n <= 0: return
    tn = np.linspace(0, dur, n, endpoint=False)
    tone = np.sin(2 * np.pi * 185.0 * tn) * np.exp(-tn * 25.0) * 0.4
    noise = (np.random.rand(n) * 2 - 1) * np.exp(-tn * 16.0) * 0.6
    sig = (tone + noise) * amp
    left[start_idx:end_idx] += sig * 0.7
    right[start_idx:end_idx] += sig * 0.7

def add_hihat(start_t, amp=0.25, open_hh=False):
    dur = 0.25 if open_hh else 0.06
    start_idx = int(start_t * SAMPLE_RATE)
    end_idx = min(TOTAL_SAMPLES, int((start_t + dur) * SAMPLE_RATE))
    n = end_idx - start_idx
    if n <= 0: return
    tn = np.linspace(0, dur, n, endpoint=False)
    decay = 12.0 if open_hh else 55.0
    noise = (np.random.rand(n) * 2 - 1) * np.exp(-tn * decay) * amp
    pan = 0.2 if (start_idx % 2 == 0) else -0.2
    left[start_idx:end_idx] += noise * (0.7 - pan*0.2)
    right[start_idx:end_idx] += noise * (0.7 + pan*0.2)

def add_808(start_t, freq=43.65, dur=0.6, amp=0.8):
    start_idx = int(start_t * SAMPLE_RATE)
    end_idx = min(TOTAL_SAMPLES, int((start_t + dur) * SAMPLE_RATE))
    n = end_idx - start_idx
    if n <= 0: return
    tn = np.linspace(0, dur, n, endpoint=False)
    pitch_env = np.exp(-tn * 35.0) * 40.0
    phase = 2 * np.pi * np.cumsum(freq + pitch_env) / SAMPLE_RATE
    amp_env = np.exp(-tn * 4.5)
    # Saturation
    sig = np.tanh(np.sin(phase) * 1.5) * amp_env * amp
    left[start_idx:end_idx] += sig * 0.75
    right[start_idx:end_idx] += sig * 0.75

# 1. Intro atmospheric chords (0.0s - 3.8s)
# F minor: F (174.6), Ab (207.65), C (261.63), Eb (311.13)
for chord_time in [0.0, 1.8]:
    add_tone(174.61, chord_time, 1.8, amp=0.18, wave_type='saw', pan=-0.3)
    add_tone(207.65, chord_time, 1.8, amp=0.18, wave_type='saw', pan=0.3)
    add_tone(261.63, chord_time, 1.8, amp=0.15, wave_type='triangle', pan=0.0)
    add_tone(311.13, chord_time, 1.8, amp=0.12, wave_type='sine', pan=-0.1)

# Glitch FX in Intro
for t_click in [0.5, 1.1, 1.7, 2.3, 2.9, 3.4]:
    add_noise(t_click, 0.03, amp=0.08, pan=0.2)

# Build riser leading to drop at 3.8s
riser_start = 2.4
riser_n = int(1.4 * SAMPLE_RATE)
rn = np.linspace(0, 1.4, riser_n)
rfreq = np.linspace(150, 900, riser_n)
rphase = 2 * np.pi * np.cumsum(rfreq) / SAMPLE_RATE
renv = np.linspace(0.01, 0.35, riser_n)
rsig = (np.sin(rphase) + (np.random.rand(riser_n)*2-1)*0.2) * renv
r_start_idx = int(riser_start * SAMPLE_RATE)
left[r_start_idx:r_start_idx+riser_n] += rsig * 0.6
right[r_start_idx:r_start_idx+riser_n] += rsig * 0.6

# 2. Impact / Sub-Drop at 3.8s (THE REVEAL)
add_808(3.8, freq=38.0, dur=1.8, amp=1.0)
add_noise(3.8, 0.5, amp=0.4)
add_kick(3.8, amp=0.9)

# 3. Main Beat Loop (from 4.0s to 18.0s)
# 120 BPM: beats at 4.0, 4.5, 5.0, 5.5, etc.
notes_f_minor = [174.61, 207.65, 233.08, 261.63, 311.13, 349.23, 415.30] # F3, Ab3, Bb3, C4, Eb4, F4, Ab4
cur_t = 4.0
while cur_t < 17.5:
    beat_in_bar = int(round((cur_t - 4.0) / BEAT_DUR)) % 4
    
    # Drums
    if beat_in_bar in [0, 2]: # Kick on 1 and 3 (with syncopation)
        add_kick(cur_t, amp=0.75)
        if cur_t + 0.375 < 17.5 and (int((cur_t - 4.0) / BAR_DUR) % 2 == 1):
            add_kick(cur_t + 0.375, amp=0.5) # syncopated extra kick
    if beat_in_bar in [1, 3]: # Snare on 2 and 4
        add_snare(cur_t, amp=0.6)
        
    # 808 Bass
    if beat_in_bar == 0:
        note_freq = 43.65 if (int((cur_t - 4.0) / BAR_DUR) % 2 == 0) else 38.89 # F1 or Eb1
        add_808(cur_t, freq=note_freq, dur=0.8, amp=0.8)
    elif beat_in_bar == 2:
        add_808(cur_t, freq=48.99, dur=0.6, amp=0.7) # G1/Ab1
        
    # 16th-note Hi-Hats
    for i in range(4):
        hh_t = cur_t + i * (BEAT_DUR / 4.0)
        if hh_t < 17.5:
            is_open = (i == 2 and beat_in_bar % 2 == 1)
            add_hihat(hh_t, amp=0.25 if i % 2 == 0 else 0.14, open_hh=is_open)
            
    # Melodic Arp Lead
    step = int((cur_t - 4.0) / (BEAT_DUR / 2.0))
    n_idx = (step * 3) % len(notes_f_minor)
    melody_freq = notes_f_minor[n_idx] * 1.5
    add_tone(melody_freq, cur_t, 0.22, amp=0.15, wave_type='saw', pan=((step % 4) - 1.5) * 0.3)
    add_tone(melody_freq * 0.5, cur_t, 0.22, amp=0.12, wave_type='square', pan=0.0)

    cur_t += BEAT_DUR

# Scene transition SFX hits
add_noise(8.0, 0.35, amp=0.25, pan=0.3)   # Scene 3 transition whoosh
add_tone(523.25, 8.0, 0.4, amp=0.2, wave_type='sine') # C5 chime
add_noise(12.5, 0.35, amp=0.25, pan=-0.3) # Scene 4 transition
add_tone(659.25, 12.5, 0.4, amp=0.2, wave_type='sine') # E5 chime
add_noise(16.5, 0.5, amp=0.35, pan=0.0)   # Scene 5 CTA transition
add_808(16.5, freq=43.65, dur=2.5, amp=0.85)

# Outro chord & gentle fade (17.5s - 20.0s)
outro_notes = [349.23, 415.30, 523.25, 698.46] # F4, Ab4, C5, F5
for on in outro_notes:
    add_tone(on, 17.0, 2.8, amp=0.15, wave_type='sine')
    add_tone(on, 17.0, 2.8, amp=0.12, wave_type='triangle')

# Master compression / limiting & fadeout
mix_l = np.tanh(left * 1.1)
mix_r = np.tanh(right * 1.1)

# Smooth master fade out at very end (19.4s - 20.0s)
fade_start = int(19.4 * SAMPLE_RATE)
fade_len = TOTAL_SAMPLES - fade_start
fade_curve = np.linspace(1.0, 0.0, fade_len)
mix_l[fade_start:] *= fade_curve
mix_r[fade_start:] *= fade_curve

# Normalize to -0.5 dB
peak = max(np.max(np.abs(mix_l)), np.max(np.abs(mix_r)))
if peak > 0:
    gain = 0.94 / peak
    mix_l *= gain
    mix_r *= gain

# Write 16-bit WAV
with wave.open("brag-output/work/soundtrack.wav", "wb") as wf:
    wf.setnchannels(2)
    wf.setsampwidth(2)
    wf.setframerate(SAMPLE_RATE)
    raw = bytearray()
    for s_l, s_r in zip(mix_l, mix_r):
        int_l = int(max(-32767, min(32767, s_l * 32767)))
        int_r = int(max(-32767, min(32767, s_r * 32767)))
        raw.extend(struct.pack('<hh', int_l, int_r))
    wf.writeframes(raw)

print("Soundtrack generated successfully: brag-output/work/soundtrack.wav")
