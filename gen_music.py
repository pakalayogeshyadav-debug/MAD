import wave
import struct
import math

# Generate a 10-second arpeggiated music sequence
sample_rate = 44100
duration = 10.0
frequency_sequence = [261.63, 329.63, 392.00, 523.25] # C4, E4, G4, C5

num_samples = int(sample_rate * duration)
amplitude = 16000

wav_path = "app/src/main/res/raw/demo_music.wav"
import os
os.makedirs(os.path.dirname(wav_path), exist_ok=True)

with wave.open(wav_path, 'w') as wav_file:
    wav_file.setnchannels(1) # Mono
    wav_file.setsampwidth(2) # 2 bytes per sample
    wav_file.setframerate(sample_rate)

    for i in range(num_samples):
        # Change frequency every 0.25 seconds
        freq_idx = int(i / (sample_rate * 0.25)) % len(frequency_sequence)
        freq = frequency_sequence[freq_idx]
        
        # Envelope to prevent clicking
        time_in_note = (i % int(sample_rate * 0.25)) / sample_rate
        envelope = math.exp(-3.0 * time_in_note) # Decay
        
        value = int(amplitude * envelope * math.sin(2.0 * math.pi * freq * (i / sample_rate)))
        data = struct.pack('<h', value)
        wav_file.writeframesraw(data)
