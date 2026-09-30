package fr.lordfinn.steveparty.blocks.custom.pipe;

/** The three kinds of warp pipe, each in the 16 plastic colours. */
public enum PipeKind {
    /** All plastic. */
    OPAQUE("opaque", "pipe"),
    /** Glass with plastic edges: what travels inside shows. */
    GLASS("glass", "glass_pipe"),
    /** A plastic frame with a glass window in the middle of each face. */
    WINDOWED("windowed", "windowed_pipe");

    /** Folder of its textures ({@code textures/block/pipe/<folder>/}). */
    public final String folder;
    /** Suffix of its block ids ({@code <color>_<suffix>}). */
    public final String suffix;

    PipeKind(String folder, String suffix) {
        this.folder = folder;
        this.suffix = suffix;
    }
}
