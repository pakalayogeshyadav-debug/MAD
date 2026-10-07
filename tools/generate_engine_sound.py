import wave
import math
import struct
import os
import random

def generate_wav(filename):
    nchannels = 1
    sampwidth = 2
    framerate = 44100
    duration = 30.0
    audio_data = bytearray()

    for i in range(int(framerate * duration)):
        t = float(i) / framerate

        # Engine RPM simulation: idle for 5s, rev up to 15s, drop down, rev again
        if t < 5.0:
            rpm = 800.0
        elif t < 10.0:
            rpm = 800.0 + (t - 5.0) * 800.0 # Rev up to 4800
        elif t < 15.0:
            rpm = 4800.0
        elif t < 17.0:
            rpm = 4800.0 - (t - 15.0) * 2000.0 # Drop back to 800
        elif t < 22.0:
            rpm = 800.0 + 2000.0 * math.sin((t-17.0) * math.pi) # Revs
        else:
            rpm = 800.0

        freq = rpm / 60.0 * 2.0 # 4-stroke 8-cylinder roughly

        # Low frequency sawtooth wave for engine hum
        phase = (t * freq) % 1.0
        val = (2.0 * phase - 1.0)

        # Add random noise for rumble/growl texture
        noise = random.uniform(-1.0, 1.0)
        val = val * 0.7 + noise * 0.3

        # Envelope to prevent clicking at start/end
        envelope = 1.0
        if t < 0.1: envelope = t / 0.1
        if t > duration - 0.1: envelope = (duration - t) / 0.1

        sample = val * envelope
        amplitude = int(sample * 16000)
        amplitude = max(-32768, min(32767, amplitude))

        audio_data.extend(struct.pack('<h', amplitude))

    os.makedirs(os.path.dirname(filename), exist_ok=True)
    with wave.open(filename, 'w') as wav_file:
        wav_file.setnchannels(nchannels)
        wav_file.setsampwidth(sampwidth)
        wav_file.setframerate(framerate)
        wav_file.writeframes(audio_data)
    print(f"Generated {filename}")

if __name__ == '__main__':
    target_dir = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'raw')
    generate_wav(os.path.join(target_dir, 'engine_sound.wav'))
