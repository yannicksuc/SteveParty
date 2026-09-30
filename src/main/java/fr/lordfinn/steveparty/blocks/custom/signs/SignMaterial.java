package fr.lordfinn.steveparty.blocks.custom.signs;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a material sign can be made of. Any block of the kind's tag works, modded ones included: mods add their
 * planks to {@code minecraft:planks} and their rocks to the {@code c:} convention tags pulled in by
 * {@code steveparty:rock_sign_materials}.
 */
public enum SignMaterial {
    WOOD(BlockTags.PLANKS, Blocks.OAK_PLANKS),
    ROCK(TagKey.of(RegistryKeys.BLOCK, Steveparty.id("rock_sign_materials")), Blocks.STONE);

    private final TagKey<Block> tag;
    private final Block defaultBlock;

    SignMaterial(TagKey<Block> tag, Block defaultBlock) {
        this.tag = tag;
        this.defaultBlock = defaultBlock;
    }

    public TagKey<Block> tag() {
        return tag;
    }

    public Block defaultBlock() {
        return defaultBlock;
    }

    public Identifier defaultId() {
        return Registries.BLOCK.getId(defaultBlock);
    }

    public boolean accepts(Block block) {
        return block.getDefaultState().isIn(tag);
    }

    /** @return the material block of this stack (a block item of the tag), or null. */
    public @Nullable Block materialOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem && accepts(blockItem.getBlock())) return blockItem.getBlock();
        return null;
    }

    /**
     * @return {@code id} if it names a registered block of this kind, the default material otherwise
     * (unknown id, or a mod that was removed since).
     */
    public Identifier resolve(@Nullable Identifier id) {
        if (id == null) return defaultId();
        Block block = Registries.BLOCK.getOptionalValue(id).orElse(null);
        return block != null && accepts(block) ? id : defaultId();
    }

    public Block resolveBlock(@Nullable Identifier id) {
        return Registries.BLOCK.get(resolve(id));
    }

    /** @return every block of this kind, in registry order (for the creative inventory). */
    public List<Block> allBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (RegistryEntry<Block> entry : Registries.BLOCK.iterateEntries(tag)) blocks.add(entry.value());
        return blocks;
    }
}
