import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Gera a trilha e os efeitos originais usados pelo prototipo, sem assets externos. */
public final class GenerateAudio {

    private static final int SAMPLE_RATE = 44_100;

    public static void main(String[] args) throws Exception {
        Path assets = args.length == 0 ? Path.of("project", "assets") : Path.of(args[0]);
        Path music = Files.createDirectories(assets.resolve("music"));
        Path sounds = Files.createDirectories(assets.resolve("sounds"));
        write(music.resolve("tempest_forest_theme.wav"), 32.0, GenerateAudio::musicSample);
        write(sounds.resolve("water_blade.wav"), 0.42, (t, r) -> sweep(t, 0.42, 840, 180) * 0.55 + noise(r) * envelope(t, 0.42) * 0.18);
        write(sounds.resolve("katana_slash.wav"), 0.32, (t, r) -> sweep(t, 0.32, 1700, 240) * 0.45 + noise(r) * envelope(t, 0.32) * 0.34);
        write(sounds.resolve("predator.wav"), 0.68, (t, r) -> (sine(58, t) + sine(71, t) * 0.7) * envelope(t, 0.68) * 0.48 + noise(r) * 0.08);
        write(sounds.resolve("black_lightning.wav"), 0.48, (t, r) -> noise(r) * envelope(t, 0.48) * (0.56 + sine(31, t) * 0.22));
        write(sounds.resolve("level_up.wav"), 0.72, (t, r) -> arpeggio(t, new double[]{440, 554.37, 659.25, 880}, 0.18) * envelope(t, 0.72) * 0.48);
        write(sounds.resolve("transformation.wav"), 1.45, (t, r) -> (sweep(t, 1.45, 90, 720) + sine(180, t) * 0.45) * envelope(t, 1.45) * 0.44);
        write(sounds.resolve("ranga_call.wav"), 1.10, (t, r) -> sweep(t, 1.10, 210, 82) * envelope(t, 1.10) * 0.52 + sine(54, t) * 0.16);
        write(sounds.resolve("ranga_bite.wav"), 0.28, (t, r) -> noise(r) * envelope(t, 0.28) * 0.45 + sine(92, t) * 0.30);
        write(sounds.resolve("rimuru_hit.wav"), 0.24, (t, r) -> sine(130, t) * envelope(t, 0.24) * 0.46 + noise(r) * 0.12);
        write(sounds.resolve("boss_warning.wav"), 1.55, (t, r) -> (sine(55, t) + sine(82.41, t) * 0.65) * envelope(t, 1.55) * 0.48);
        write(sounds.resolve("victory.wav"), 2.4, (t, r) -> arpeggio(t, new double[]{293.66, 369.99, 440, 587.33, 739.99}, 0.30) * envelope(t, 2.4) * 0.50);
    }

    private static double musicSample(double t, Random random) {
        double[] melody = {293.66, 349.23, 440.00, 523.25, 440.00, 392.00, 349.23, 293.66,
                261.63, 293.66, 349.23, 440.00, 392.00, 349.23, 293.66, 261.63};
        int step = (int) (t / 0.5) % melody.length;
        double local = t % 0.5;
        double lead = (sine(melody[step], t) * 0.65 + sine(melody[step] * 2, t) * 0.16)
                * Math.min(1, local * 16) * Math.min(1, (0.5 - local) * 7);
        double bass = sine(melody[(step / 4) * 4] / 4, t) * 0.30;
        double pad = (sine(146.83, t) + sine(220, t) + sine(293.66, t)) * 0.07;
        double pulse = (t % 0.5) < 0.055 ? noise(random) * (1 - (t % 0.5) / 0.055) * 0.12 : 0;
        return lead * 0.40 + bass + pad + pulse;
    }

    private static double arpeggio(double t, double[] notes, double stepSeconds) {
        int index = Math.min(notes.length - 1, (int) (t / stepSeconds));
        return sine(notes[index], t) + sine(notes[index] * 2, t) * 0.22;
    }

    private static double sweep(double t, double duration, double from, double to) {
        double frequency = from + (to - from) * t / duration;
        return Math.sin(2 * Math.PI * frequency * t) * envelope(t, duration);
    }

    private static double sine(double frequency, double t) {
        return Math.sin(2 * Math.PI * frequency * t);
    }

    private static double envelope(double t, double duration) {
        double attack = Math.min(1, t / Math.min(0.04, duration * 0.15));
        double release = Math.min(1, (duration - t) / Math.min(0.16, duration * 0.35));
        return Math.max(0, attack * release);
    }

    private static double noise(Random random) {
        return random.nextDouble() * 2 - 1;
    }

    private static void write(Path path, double seconds, SampleSource source) throws Exception {
        int samples = (int) (seconds * SAMPLE_RATE);
        byte[] pcm = new byte[samples * 2];
        Random random = new Random(path.getFileName().toString().hashCode());
        for (int i = 0; i < samples; i++) {
            double value = Math.tanh(source.sample(i / (double) SAMPLE_RATE, random));
            short sample = (short) Math.round(value * Short.MAX_VALUE * 0.72);
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >>> 8) & 0xFF);
        }
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(pcm), format, samples)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, new File(path.toString()));
        }
    }

    @FunctionalInterface
    private interface SampleSource {
        double sample(double time, Random random);
    }
}
