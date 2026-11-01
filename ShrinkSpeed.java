package id.zetminigames.pof;

public enum ShrinkSpeed {
    PELAN("Pelan", 300),
    SEDANG("Sedang", 150),
    CEPAT("Cepat", 60);

    public final String label;
    public final int durationSec;

    ShrinkSpeed(String label, int durationSec) {
        this.label = label;
        this.durationSec = durationSec;
    }

    public static ShrinkSpeed fromId(String id) {
        try {
            return valueOf(String.valueOf(id));
        } catch (IllegalArgumentException e) {
            return SEDANG;
        }
    }
}
