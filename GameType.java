package id.zetminigames.core;

public enum GameType {
    POF("Pillar of Fortune", 2, 20, 1),
    DEATHSWAP("Deathswap", 2, 10, 1),
    MACE("Mace Roulette", 2, 10, 1),
    BATTLEROYALE("Battle Royale", 8, 48, 1);

    public final String label;
    public final int min;
    public final int max;
    public final int testMin;

    GameType(String label, int min, int max, int testMin) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.testMin = testMin;
    }
}
