package fr.lordfinn.steveparty.client.screens.pageeditor;

import fr.lordfinn.steveparty.client.gui.paint.Ramp;

/** The four dividers of the page editor: their name's key, their colour, their icon (8 x 8, centred in the visible part). */
public enum BinderTab {
    PAGE("page", Ramp.of(0x004a50, 0x8ff0f6, 0x00b3bd, 0x008c95),
            new String[]{".#####..", ".#...##.", ".#....#.", ".#.##.#.", ".#....#.", ".#.##.#.", ".#....#.", ".######."}),
    FORMATS("formats", Ramp.of(0x2e0a4a, 0xe3c6ff, 0xa35cff, 0x7a38d0),
            new String[]{"........", ".#....#.", "###..###", ".#....#.", "###..###", "###..###", "###..###", "........"}),
    PIPES("pipes", Ramp.of(0x004a33, 0x8ff5d0, 0x00c792, 0x00ac82),
            new String[]{"########", "#......#", "########", ".#....#.", ".#....#.", ".#....#.", ".#....#.", ".######."}),
    RESULTS("podiums", Ramp.of(0x5a2800, 0xffd6a0, 0xfda757, 0xd88029),
            new String[]{"........", "...##...", "...##...", "######..", "######..", "########", "########", "........"});

    final String key;
    final Ramp ramp;
    final String[] icon;

    BinderTab(String key, Ramp ramp, String[] icon) {
        this.key = key;
        this.ramp = ramp;
        this.icon = icon;
    }

    /** The key of its texts: {@code tab.<key>}, {@code <key>.info}. */
    public String key() {
        return key;
    }
}
