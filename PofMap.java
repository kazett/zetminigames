package id.zetminigames.pof;

import net.kyori.adventure.text.format.NamedTextColor;

/** Jenis map (lantai di bawah pilar) Pillar of Fortune. */
public enum PofMap {
    LAVA("Lava Rising", NamedTextColor.RED),
    SLIME("Slime", NamedTextColor.GREEN),
    MAGMA("Magma", NamedTextColor.GOLD),
    GRASS("Grass", NamedTextColor.DARK_GREEN),
    VOID("Void", NamedTextColor.GRAY),
    COBWEB("Cobweb", NamedTextColor.WHITE),
    TNT("TNT", NamedTextColor.DARK_RED),
    SNOW("Powder Snow", NamedTextColor.AQUA);

    public final String label;
    public final NamedTextColor color;

    PofMap(String label, NamedTextColor color) {
        this.label = label;
        this.color = color;
    }

    /** null = random / id gak dikenal. */
    public static PofMap fromId(String id) {
        try {
            return valueOf(String.valueOf(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
