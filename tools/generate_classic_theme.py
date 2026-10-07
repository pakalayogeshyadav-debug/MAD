import wave
import math
import struct
import os

def generate_wav(filename):
    # Parameters
    nchannels = 1
    sampwidth = 2
    framerate = 44100

    # Melody: A simple classical progression (e.g. C major / A minor arpeggios)
    # Notes: (frequency, duration in seconds)
    C4 = 261.63
    E4 = 329.63
    G4 = 392.00
    C5 = 523.25
    B4 = 493.88
    A4 = 440.00
    F4 = 349.23
    D4 = 293.66

    melody = [
        (C4, 0.5), (E4, 0.5), (G4, 0.5), (C5, 1.0),
        (G4, 0.5), (E4, 0.5), (C4, 1.0),
        (A4, 0.5), (C5, 0.5), (E4, 0.5), (A4, 1.0),
        (F4, 0.5), (A4, 0.5), (C5, 0.5), (F4, 1.0),
        (G4, 0.5), (B4, 0.5), (D4, 0.5), (G4, 1.0),
        (C4, 0.5), (E4, 0.5), (G4, 0.5), (C5, 1.5),

        # phrase 2
        (C5, 0.5), (B4, 0.5), (A4, 0.5), (G4, 1.0),
        (F4, 0.5), (E4, 0.5), (D4, 0.5), (C4, 1.5),
        (C4, 0.5), (E4, 0.5), (G4, 0.5), (C5, 2.0)
    ]

    # Generate audio data
    audio_data = bytearray()

    for freq, duration in melody:
        nframes = int(framerate * duration)
        for i in range(nframes):
            # Synthesize tone: triangle + sine harmonics for piano-like bell sound
            t = float(i) / framerate
            # Base sine
            val = math.sin(2.0 * math.pi * freq * t)
            # Add some harmonics
            val += 0.5 * math.sin(2.0 * math.pi * (freq * 2) * t)
            val += 0.25 * math.sin(2.0 * math.pi * (freq * 3) * t)

            # Envelope (Attack, Decay, Sustain, Release)
            # Simple envelope: sharp attack, exponential decay
            envelope = math.exp(-2.0 * t)

            sample = val * envelope
            # Normalize to 16-bit PCM and boost volume significantly
            amplitude = int(sample * 30000)
            # Clip
            amplitude = max(-32768, min(32767, amplitude))

            # Pack as little-endian short
            audio_data.extend(struct.pack('<h', amplitude))

    # Write to WAV file
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    with wave.open(filename, 'w') as wav_file:
        wav_file.setnchannels(nchannels)
        wav_file.setsampwidth(sampwidth)
        wav_file.setframerate(framerate)
        wav_file.writeframes(audio_data)
    print(f"Generated {filename}")

if __name__ == '__main__':
    target_dir = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'raw')
    generate_wav(os.path.join(target_dir, 'classic_theme.wav'))