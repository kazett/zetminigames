package id.zetminigames.pof;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Tata letak pilar Pillar of Fortune (tanpa dependensi Bukkit, jadi gampang dites).
 *
 * Aturan:
 * - Sebagian pilar (cincin luar, maks {@link #MAX_OUTER}) berdiri melingkar di pinggir.
 * - Sisanya di dalam cincin, di mana aja asal jarak ke pilar lain >= spacing.
 * - Cincin dibikin sekecil mungkin (gak kejauhan). Kalau 1 cincin penuh ternyata lebih kecil
 *   (pemain sedikit, 8-11), ya pakai 1 cincin aja.
 * Koordinat relatif ke titik (0,0).
 */
public final class PillarLayout {
    private PillarLayout() {}

    /** Jumlah pilar maksimal di cincin luar. */
    public static final int MAX_OUTER = 10;
    /** Sampai segini pemain, semuanya di cincin (gak ada pilar di dalam). */
    public static final int RING_ONLY_MAX = 6;

    /** @return posisi {x, z} sebanyak n, semua berjarak >= spacing satu sama lain. */
    public static List<int[]> compute(int n, double spacing, long seed) {
        Random rng = new Random(seed);
        double angle0 = rng.nextDouble() * Math.PI * 2.0;
        List<int[]> single = singleRing(n, spacing, angle0);
        if (n <= RING_ONLY_MAX) return single;

        int outer = Math.min(MAX_OUTER, Math.max(RING_ONLY_MAX, (n + 1) / 2));
        int inner = n - outer;
        double rMin = spacing / (2.0 * Math.sin(Math.PI / outer));
        double singleR = maxRadius(single);

        // Cincin luar + cincin dalam (selang-seling) + opsional 1 pilar di tengah; R dicari dari yang terkecil
        for (double r = rMin; r < singleR; r += 0.5) {
            List<int[]> ring = ring(outer, r, angle0);
            if (minDist(ring) < spacing) continue;      // pembulatan bikin jarak kurang
            List<int[]> fill = structuredInside(ring, inner, r, spacing, angle0);
            if (fill == null) fill = fillInside(ring, inner, r, spacing, rng);
            if (fill != null) {
                List<int[]> all = new ArrayList<>(ring);
                all.addAll(fill);
                return all;
            }
        }
        return single;   // 1 cincin penuh lebih kecil / satu-satunya yang muat
    }

    private static List<int[]> singleRing(int n, double spacing, double angle0) {
        double r = n == 1 ? 0.0 : spacing / (2.0 * Math.sin(Math.PI / n));
        for (; r < 500; r += 0.25) {
            List<int[]> ring = ring(n, r, angle0);
            if (n == 1 || minDist(ring) >= spacing) return ring;
        }
        return ring(n, r, angle0);
    }

    /** Isi dalam yang rapi: 1 cincin kecil (selang-seling sama cincin luar) + opsional 1 pilar di tengah. */
    private static List<int[]> structuredInside(List<int[]> outerRing, int m, double rOuter, double spacing, double angle0) {
        for (int withCenter = 0; withCenter <= 1; withCenter++) {
            int cnt = m - withCenter;
            if (cnt < 0 || cnt == 1) continue;
            List<int[]> centerPt = new ArrayList<>();
            if (withCenter == 1) centerPt.add(new int[]{0, 0});
            if (cnt == 0) {
                if (valid(centerPt, outerRing, spacing)) return centerPt;
                continue;
            }
            double rIMin = spacing / (2.0 * Math.sin(Math.PI / cnt));
            double step = 2.0 * Math.PI / cnt;
            for (double rI = rIMin; rI < rOuter; rI += 0.5) {
                for (int k = 0; k < 24; k++) {
                    List<int[]> cand = ring(cnt, rI, angle0 + step * k / 24.0);
                    cand.addAll(centerPt);
                    if (valid(cand, outerRing, spacing)) return cand;
                }
            }
        }
        return null;
    }

    private static boolean valid(List<int[]> inner, List<int[]> outerRing, double spacing) {
        if (inner.size() > 1 && minDist(inner) < spacing) return false;
        for (int[] a : inner) {
            for (int[] b : outerRing) {
                if (dist(a[0], a[1], b[0], b[1]) < spacing) return false;
            }
        }
        return true;
    }

    private static List<int[]> ring(int k, double r, double angle0) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            double a = angle0 + 2.0 * Math.PI * i / k;
            out.add(new int[]{(int) Math.round(r * Math.cos(a)), (int) Math.round(r * Math.sin(a))});
        }
        return out;
    }

    /** Taruh m pilar di dalam lingkaran radius r, jarak >= spacing dari ring dan satu sama lain. Null kalau gak muat. */
    private static List<int[]> fillInside(List<int[]> ring, int m, double r, double spacing, Random rng) {
        int lim = (int) Math.floor(r);
        List<int[]> cands = new ArrayList<>();
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                if (x * x + z * z > r * r) continue;
                boolean ok = true;
                for (int[] p : ring) {
                    if (dist(p[0], p[1], x, z) < spacing) { ok = false; break; }
                }
                if (ok) cands.add(new int[]{x, z});
            }
        }
        if (cands.size() < m) return null;
        // beberapa urutan berbeda: dari tengah keluar, dari luar masuk, dan acak
        for (int attempt = 0; attempt < 60; attempt++) {
            List<int[]> order = new ArrayList<>(cands);
            if (attempt == 0) order.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
            else if (attempt == 1) order.sort((a, b) -> Integer.compare(b[0] * b[0] + b[1] * b[1], a[0] * a[0] + a[1] * a[1]));
            else Collections.shuffle(order, rng);
            List<int[]> picked = new ArrayList<>();
            for (int[] c : order) {
                boolean ok = true;
                for (int[] p : picked) {
                    if (dist(p[0], p[1], c[0], c[1]) < spacing) { ok = false; break; }
                }
                if (ok) {
                    picked.add(c);
                    if (picked.size() == m) return picked;
                }
            }
        }
        return null;
    }

    private static double dist(int ax, int az, int bx, int bz) {
        return Math.hypot(ax - bx, az - bz);
    }

    public static double minDist(List<int[]> pts) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < pts.size(); i++) {
            for (int j = i + 1; j < pts.size(); j++) {
                min = Math.min(min, dist(pts.get(i)[0], pts.get(i)[1], pts.get(j)[0], pts.get(j)[1]));
            }
        }
        return min;
    }

    /** Jarak terjauh dari titik (0,0). */
    public static double maxRadius(List<int[]> pts) {
        double max = 0;
        for (int[] p : pts) max = Math.max(max, Math.hypot(p[0], p[1]));
        return max;
    }
}
