package id.zetminigames.battleroyale;

import java.util.Random;

/**
 * Jadwal zona Battle Royale (tanpa dependensi Bukkit, jadi gampang dites).
 *
 * - Jumlah tahap diacak tiap game (minStages..maxStages), gak ditampilin ke pemain.
 * - Waktu tunggu + lama mengecil tiap tahap diacak.
 * - Ukuran tiap tahap diacak (selalu mengecil), tahap terakhir PASTI finalSize.
 * - Tahap terakhir PASTI selesai tepat di detik totalSec.
 * - Sesudah itu ada tahap paksa (ukuran kecil banget) tiap forceGap detik.
 */
public final class ZonePlan {
    public final int baseStages;
    /** Detik (sejak mulai) zona mulai mengecil, per tahap (termasuk tahap paksa). */
    public final int[] shrinkAt;
    /** Lama mengecil (detik) per tahap. */
    public final int[] shrinkLen;
    /** Ukuran zona sesudah tahap itu selesai. */
    public final int[] sizes;
    public final int totalSec;

    public int totalSeconds() { return totalSec; }

    private ZonePlan(int baseStages, int[] shrinkAt, int[] shrinkLen, int[] sizes, int totalSec) {
        this.baseStages = baseStages;
        this.shrinkAt = shrinkAt;
        this.shrinkLen = shrinkLen;
        this.sizes = sizes;
        this.totalSec = totalSec;
    }

    public static ZonePlan generate(Random random, int minStages, int maxStages,
                                    double startSize, double finalSize, int totalSec,
                                    double minWait, double minShrink,
                                    int forceGap, int forceShrink, int[] forceSizes) {
        int lo = Math.max(2, Math.min(minStages, maxStages));
        int hi = Math.max(lo, Math.max(minStages, maxStages));
        int n = lo + random.nextInt(hi - lo + 1);
        int total = Math.max(n * 2, totalSec);

        double scale = Math.min(1.0, total / (n * (minWait + minShrink)));
        minWait *= scale;
        minShrink *= scale;
        double spare = total - n * (minWait + minShrink);

        double[] weights = new double[n * 2];
        double sum = 0.0;
        for (int k = 0; k < weights.length; k++) {
            weights[k] = 0.15 - Math.log(1.0 - random.nextDouble());
            sum += weights[k];
        }

        int forced = forceSizes.length;
        int[] at = new int[n + forced];
        int[] len = new int[n + forced];
        int[] sz = new int[n + forced];

        // ukuran: tiap tahap ngecilin dengan faktor acak, total pas ke finalSize
        double[] f = new double[n];
        double fs = 0.0;
        for (int i = 0; i < n; i++) {
            f[i] = 0.6 + random.nextDouble() * 0.8;
            fs += f[i];
        }
        double logTotal = Math.log(finalSize / startSize);
        double acc = 0.0;
        for (int i = 0; i < n; i++) {
            acc += f[i];
            double size = startSize * Math.exp(logTotal * acc / fs);
            sz[i] = (int) (Math.round(size / 5.0) * 5L);
        }
        sz[n - 1] = (int) finalSize;
        for (int i = n - 2; i >= 0; i--) {          // selalu lebih besar dari tahap sesudahnya
            if (sz[i] < sz[i + 1] + 5) sz[i] = sz[i + 1] + 5;
        }

        double t = 0.0;
        for (int i = 0; i < n; i++) {
            double wait = minWait + spare * weights[i * 2] / sum;
            double shrink = minShrink + spare * weights[i * 2 + 1] / sum;
            t += wait;
            at[i] = (int) Math.round(t);
            len[i] = Math.max(1, (int) Math.round(shrink));
            t += shrink;
        }
        len[n - 1] = Math.max(1, total - at[n - 1]);   // tahap terakhir selesai tepat di total

        int gap = Math.max(2, forceGap);
        int fl = Math.max(1, Math.min(forceShrink, gap - 1));
        for (int k = 0; k < forced; k++) {
            int end = total + gap * (k + 1);
            at[n + k] = end - fl;
            len[n + k] = fl;
            sz[n + k] = Math.max(1, forceSizes[k]);
        }
        return new ZonePlan(n, at, len, sz, total);
    }
}
