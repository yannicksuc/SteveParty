package fr.lordfinn.steveparty.minigame.zone;

import fr.lordfinn.steveparty.config.ServerConfig;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What a server does not allow in a mini-game zone: blocks, items and entities of other mods that would move things
 * across the border behind the bubble's back (remote storage, teleporters, item pipes...). Each kind is declared two
 * ways, both honoured: a tag ({@code steveparty:zone_forbidden} for blocks, items and entity types, for data
 * packs) and a list of {@code config/steveparty.json} ({@code miniGameBubbleForbiddenBlocks}, {@code ...Items},
 * {@code ...Entities}), whose entries are an id ({@code modid:name}), a tag ({@code #namespace:tag}) or a whole
 * mod ({@code modid:*}).
 * <ul>
 *     <li>a zone holding a forbidden block or entity starts no session ({@link #findBlock}, and the entities a
 *     session reads when it begins): the refusal names it and says where it is;</li>
 *     <li>during a session no forbidden block appears in the zone and no forbidden entity spawns in it (none comes
 *     in either: nothing does);</li>
 *     <li>a member of a session picks up, uses and takes from a container no forbidden item.</li>
 * </ul>
 * The lists are read when the server starts; the tags are looked at as they are (a data pack reload changes them).
 */
public final class ZoneForbidden {
    private static final Identifier ID = Steveparty.id("zone_forbidden");
    /** How deep items held in items are looked into (a bundle in a shulker box in a shulker box...). */
    private static final int MAX_NESTING = 4;
    public static final TagKey<Block> BLOCKS = TagKey.of(RegistryKeys.BLOCK, ID);
    public static final TagKey<Item> ITEMS = TagKey.of(RegistryKeys.ITEM, ID);
    public static final TagKey<EntityType<?>> ENTITIES = TagKey.of(RegistryKeys.ENTITY_TYPE, ID);

    /** The entries of a list of the settings, once looked up. */
    private static final class Listed<T> {
        final Set<T> ones = new HashSet<>();
        final List<TagKey<T>> tags = new ArrayList<>();
    }

    private static Listed<Block> blocks = new Listed<>();
    private static Listed<Item> items = new Listed<>();
    private static Listed<EntityType<?>> entities = new Listed<>();

    /** A forbidden block found in a zone. */
    public record FoundBlock(Block block, BlockPos pos) {
    }

    private ZoneForbidden() {
    }

    /** The server starts: the lists of the settings are looked up in the registries. */
    public static void resolve() {
        ServerConfig config = ServerConfig.get();
        blocks = resolve(config.miniGameBubbleForbiddenBlocks, Registries.BLOCK, RegistryKeys.BLOCK, "miniGameBubbleForbiddenBlocks");
        items = resolve(config.miniGameBubbleForbiddenItems, Registries.ITEM, RegistryKeys.ITEM, "miniGameBubbleForbiddenItems");
        entities = resolve(config.miniGameBubbleForbiddenEntities, Registries.ENTITY_TYPE, RegistryKeys.ENTITY_TYPE, "miniGameBubbleForbiddenEntities");
    }

    private static <T> Listed<T> resolve(@Nullable List<String> entries, Registry<T> registry, RegistryKey<? extends Registry<T>> key, String setting) {
        Listed<T> listed = new Listed<>();
        if (entries == null) return listed;
        for (String entry : entries) {
            String text = entry == null ? "" : entry.trim();
            if (text.startsWith("#")) {
                Identifier tag = Identifier.tryParse(text.substring(1));
                if (tag != null) listed.tags.add(TagKey.of(key, tag));
                else Steveparty.LOGGER.warn("{}: « {} » is not a tag", setting, entry);
            } else if (text.endsWith(":*")) {
                String mod = text.substring(0, text.length() - 2);
                int before = listed.ones.size();
                for (Identifier id : registry.getIds()) if (id.getNamespace().equals(mod)) listed.ones.add(registry.get(id));
                if (listed.ones.size() == before) Steveparty.LOGGER.info("{}: nothing of « {} » on this server", setting, mod);
            } else {
                Identifier id = Identifier.tryParse(text);
                if (id != null && registry.containsId(id)) listed.ones.add(registry.get(id));
                else Steveparty.LOGGER.info("{}: « {} » is not on this server", setting, entry);
            }
        }
        return listed;
    }

    // ------------------------------------------------------------------ what is forbidden

    public static boolean isForbidden(BlockState state) {
        if (state.isAir()) return false;
        if (state.isIn(BLOCKS) || blocks.ones.contains(state.getBlock())) return true;
        for (TagKey<Block> tag : blocks.tags) if (state.isIn(tag)) return true;
        return false;
    }

    /** @return true if the item is forbidden, or holds one that is (a shulker box, a bundle, a catalogue: what they carry goes with them) */
    public static boolean isForbidden(ItemStack stack) {
        return isForbidden(stack, 0);
    }

    private static boolean isForbidden(ItemStack stack, int depth) {
        if (stack.isEmpty()) return false;
        if (stack.isIn(ITEMS) || items.ones.contains(stack.getItem())) return true;
        for (TagKey<Item> tag : items.tags) if (stack.isIn(tag)) return true;
        if (depth >= MAX_NESTING) return false;
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) for (ItemStack held : container.iterateNonEmpty()) if (isForbidden(held, depth + 1)) return true;
        BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) for (ItemStack held : bundle.iterate()) if (isForbidden(held, depth + 1)) return true;
        InventoryComponent inventory = stack.get(ModComponents.INVENTORY_COMPONENT);
        if (inventory != null) for (ItemStack held : inventory.getItems()) if (isForbidden(held, depth + 1)) return true;
        return false;
    }

    public static boolean isForbidden(EntityType<?> type) {
        if (type.isIn(ENTITIES) || entities.ones.contains(type)) return true;
        for (TagKey<EntityType<?>> tag : entities.tags) if (type.isIn(tag)) return true;
        return false;
    }

    // ------------------------------------------------------------------ looking for it in a zone

    /**
     * The first forbidden block of a zone, null if it holds none.
     * <p>
     * Never block by block over the zone: each chunk section the zone covers is asked whether its palette (the few
     * block states it is made of) holds a forbidden one, and only a section that does is walked, within the zone.
     *
     * @param load false: only the chunks that are loaded are looked at (what a screen asks again and again)
     */
    public static @Nullable FoundBlock findBlock(ServerWorld world, MiniGameZone zone, boolean load) {
        // Nothing is forbidden on this server (no list, empty tags): nothing to look for
        if (blocks.ones.isEmpty() && blocks.tags.isEmpty() && !Registries.BLOCK.iterateEntries(BLOCKS).iterator().hasNext()) return null;
        BlockBox box = zone.box();
        Predicate<BlockState> forbidden = ZoneForbidden::isForbidden;
        int firstSection = world.getSectionIndex(Math.max(box.getMinY(), world.getBottomY()));
        int lastSection = world.getSectionIndex(Math.min(box.getMaxY(), world.getTopY() - 1));
        for (int chunkX = zone.minChunkX(); chunkX <= zone.maxChunkX(); chunkX++) {
            for (int chunkZ = zone.minChunkZ(); chunkZ <= zone.maxChunkZ(); chunkZ++) {
                // the loaded chunk straight from its holder; only one that is not there is asked for (and waited for)
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null && load) chunk = world.getChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (int index = firstSection; index <= lastSection; index++) {
                    ChunkSection section = chunk.getSection(index);
                    if (section.isEmpty() || !section.hasAny(forbidden)) continue;
                    FoundBlock found = findIn(section, chunkX << 4, world.sectionIndexToCoord(index) << 4, chunkZ << 4, box);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /** The first forbidden block of a section that is in the box (its palette can hold one that is out of it, or gone). */
    private static @Nullable FoundBlock findIn(ChunkSection section, int originX, int originY, int originZ, BlockBox box) {
        int minX = Math.max(box.getMinX(), originX), maxX = Math.min(box.getMaxX(), originX + 15);
        int minY = Math.max(box.getMinY(), originY), maxY = Math.min(box.getMaxY(), originY + 15);
        int minZ = Math.max(box.getMinZ(), originZ), maxZ = Math.min(box.getMaxZ(), originZ + 15);
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                    if (isForbidden(state)) return new FoundBlock(state.getBlock(), new BlockPos(x, y, z));
                }
            }
        }
        return null;
    }

    /** The first forbidden entity among those of a zone, null if there is none. */
    static @Nullable Entity findEntity(List<Entity> inZone) {
        for (Entity entity : inZone) {
            if (isForbidden(entity.getType())) return entity;
            for (Entity rider : entity.getPassengersDeep()) if (isForbidden(rider.getType())) return rider;
        }
        return null;
    }

    static Text blockText(FoundBlock found) {
        return Text.translatable("message.steveparty.zone_bubble.forbidden_block", found.block().getName(),
                found.pos().getX(), found.pos().getY(), found.pos().getZ());
    }

    static Text entityText(EntityType<?> type, BlockPos pos) {
        return Text.translatable("message.steveparty.zone_bubble.forbidden_entity", type.getName(), pos.getX(), pos.getY(), pos.getZ());
    }
}
