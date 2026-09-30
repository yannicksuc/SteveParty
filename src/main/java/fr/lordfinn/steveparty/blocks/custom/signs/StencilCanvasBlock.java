package fr.lordfinn.steveparty.blocks.custom.signs;

/** A block whose {@link StencilCanvasBlockEntity} takes stencils (see {@link StencilInteractions}). */
public interface StencilCanvasBlock {
    /**
     * @return true if the stencil gives the block its outline (cut-out panel) instead of being painted or
     * engraved on it: dyes and glow ink then do nothing.
     */
    default boolean usesSilhouette() {
        return false;
    }
}
