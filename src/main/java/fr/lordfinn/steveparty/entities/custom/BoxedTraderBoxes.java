package fr.lordfinn.steveparty.entities.custom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The boxes a Boxed Trader spawned from his spawn egg can come in, read from the data file
 * {@code data/steveparty/boxed_trader/box_blocks.json} (a datapack can replace it).
 * <p>
 * {@code "boxes"} is a list of lines. Each line has the same chance, whatever it holds; a line is one block, or a
 * group (a block tag, or a list of blocks and tags) out of which one member is then picked at random. A block is
 * written like in commands, with its state if needed: {@code "minecraft:barrel[facing=up]"}; a tag with a {@code #}.
 * Members that can't be a box (see {@link BoxedTraderEntity#isValidBoxBlock}) are left out.
 */
public final class BoxedTraderBoxes {
    public static final Identifier FILE = Steveparty.id("boxed_trader/box_blocks.json");
    /** Lines as written in the file. */
    private static List<List<String>> entries = List.of();
    /** Lines resolved to block states (tags are only bound once the data is loaded): null until first asked. */
    @Nullable
    private static List<List<BlockState>> lines = null;

    private BoxedTraderBoxes() {
    }

    public static void initialize() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Steveparty.id("boxed_trader_boxes");
            }

            @Override
            public void reload(ResourceManager manager) {
                entries = read(manager);
                lines = null;
            }
        });
    }

    private static List<List<String>> read(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) return List.of();
        List<List<String>> read = new ArrayList<>();
        try (Reader reader = resource.get().getReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement line : json.getAsJsonArray("boxes")) {
                List<String> members = new ArrayList<>();
                if (line instanceof JsonArray group) group.forEach(member -> members.add(member.getAsString()));
                else members.add(line.getAsString());
                read.add(members);
            }
        } catch (Exception e) {
            Steveparty.LOGGER.error("Can't read the Boxed Trader boxes {}", FILE, e);
        }
        return read;
    }

    /** @return the lines, each with the block states it can give (never empty: lines without a valid box are dropped). */
    public static List<List<BlockState>> lines() {
        List<List<BlockState>> resolved = lines;
        if (resolved == null) {
            resolved = new ArrayList<>();
            for (List<String> entry : entries) {
                List<BlockState> states = new ArrayList<>();
                for (String member : entry) resolve(member, states);
                if (!states.isEmpty()) resolved.add(List.copyOf(states));
            }
            lines = resolved = List.copyOf(resolved);
        }
        return resolved;
    }

    private static void resolve(String member, List<BlockState> into) {
        if (member.startsWith("#")) {
            Identifier id = Identifier.tryParse(member.substring(1));
            if (id == null) return;
            for (RegistryEntry<Block> block : Registries.BLOCK.iterateEntries(TagKey.of(RegistryKeys.BLOCK, id))) {
                add(block.value().getDefaultState(), into);
            }
            return;
        }
        try {
            add(BlockArgumentParser.block(Registries.BLOCK, member, false).blockState(), into);
        } catch (CommandSyntaxException e) {
            Steveparty.LOGGER.warn("Unknown Boxed Trader box {}: {}", member, e.getMessage());
        }
    }

    private static void add(BlockState state, List<BlockState> into) {
        if (BoxedTraderEntity.isValidBoxBlock(state) && !into.contains(state)) into.add(state);
    }

    /** A random box: a line (all equal), then one of its members; the merchant's gold block if there is no line. */
    public static BlockState pick(Random random) {
        List<List<BlockState>> all = lines();
        if (all.isEmpty()) return Blocks.GOLD_BLOCK.getDefaultState();
        List<BlockState> line = all.get(random.nextInt(all.size()));
        return line.get(random.nextInt(line.size()));
    }
}
