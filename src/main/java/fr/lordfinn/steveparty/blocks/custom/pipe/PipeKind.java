package fr.lordfinn.steveparty.blocks.custom.pipe;

/** The kinds of warp pipe. */
public enum PipeKind {
    /** All plastic, in the 16 plastic colours. */
    OPAQUE("opaque", "pipe", true),
    /** A plastic frame along the pipe with a glass window in the middle of each face, in the 16 plastic colours. */
    WINDOWED("windowed", "windowed_pipe", true),
    /** Plain glass (the vanilla glass look), one colour only. */
    GLASS("glass", "glass_pipe", false),
    /** Stained glass (the vanilla stained glass look), in the 16 dye colours. */
    STAINED_GLASS("stained_glass", "stained_glass_pipe", true);

    /** Folder of its textures ({@code textures/block/pipe/<folder>/}, plastic kinds only). */
    public final String folder;
    /** Its block id ({@code <color>_<suffix>}, or {@code <suffix>} without colours). */
    public final String suffix;
    /** One block per colour of {@code ModBlocks.COLORS}, or a single one. */
    public final boolean colored;

    PipeKind(String folder, String suffix, boolean colored) {
        this.folder = folder;
        this.suffix = suffix;
        this.colored = colored;
    }

    public boolean isPlastic() {
        return this == OPAQUE || this == WINDOWED;
    }

    /** How many blocks of this kind. */
    public int count() {
        return colored ? 16 : 1;
    }

    /** The block id of colour {@code color} (index in {@code ModBlocks.COLORS}, ignored without colours). */
    public String id(String[] colors, int color) {
        return colored ? colors[color] + "_" + suffix : suffix;
    }
}
