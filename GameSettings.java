package id.zetminigames.core;

import id.zetminigames.pof.ShrinkSpeed;

public class GameSettings {
    public GameType type = GameType.POF;
    public static final int MIN_SWAP_SEC = 10;
    public int swapIntervalSec = 300;
    /** Mace Roulette: waktu pemegang mace buat bunuh (detik). */
    public int maceSec = 15;
    /** Interval item random (detik). */
    public int itemIntervalSec = 60;
    /** Border mulai mengecil setelah game jalan sekian detik. */
    public int shrinkStartSec = 300;
    public ShrinkSpeed speed = ShrinkSpeed.SEDANG;
    /** Map Pillar of Fortune. null = random tiap ronde. */
    public id.zetminigames.pof.PofMap map = null;
    /** Mode tes: boleh main sendirian, pilar kosong ikut dibuat. */
    public boolean testMode = false;
    public int testPillars = 4;
    /** Jumlah ronde (1 = main sekali kayak biasa). Juara = win terbanyak. */
    public int rounds = 1;
    /** Kalau win seri di akhir: true = hasil SERI, false = bonus match antar yang seri. */
    public boolean drawEnabled = false;
    /** Berhenti lebih awal kalau pemenang udah pasti (sisa ronde gak bisa dikejar). */
    public boolean skipClinched = true;
    /** Deathswap: gak boleh mati kena jebakan dengan cara yang sama dua kali (cara sama = seri, ronde diulang). */
    public boolean uniqueDeath = true;

    public static String fmt(int sec) {
        if (sec >= 60) return (sec / 60) + "m" + (sec % 60 == 0 ? "" : (sec % 60) + "s");
        return sec + "s";
    }
}
