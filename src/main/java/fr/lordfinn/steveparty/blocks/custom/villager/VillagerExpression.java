package fr.lordfinn.steveparty.blocks.custom.villager;

/**
 * The villager block's faces: 16x16 overlays drawn over its top texture (the squashed villager's face) by the block
 * entity renderer. The ordinal is the cell in textures/entity/villager_block_expressions.png (4x4 grid, built by
 * the art sources); {@link #NONE} draws nothing (the face as textured).
 */
public enum VillagerExpression {
    NONE, CLOSED, HAPPY, ANGRY, WIDE, SQUINT, DIZZY, HEARTS, GREEDY, SAD, BLUSH, SICK, STARRY, YAWN, SHOUT, DROOL
}
