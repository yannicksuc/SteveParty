package fr.lordfinn.steveparty.blocks.switchable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import fr.lordfinn.steveparty.payloads.custom.SwitchableBlocksPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Server config {@code config/steveparty/server.json}: {@code "switchable_blocks"} lists extra switchable blocks,
 * as block ids ({@code "minecraft:stone"}) or block tags ({@code "#minecraft:wool"}). Blocks with a block entity
 * are ignored. Read when the server starts and on {@code /reload}, then sent to the players (item tooltips).
 */
public final class SwitchableConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("steveparty/switchable");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("steveparty").resolve("server.json");
    private static final String KEY = "switchable_blocks";

    private SwitchableConfig() {
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> load());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            load();
            sendToAll(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
    }

    private static void load() {
        Set<Block> blocks = new LinkedHashSet<>();
        for (String entry : readEntries()) {
            try {
                if (entry.startsWith("#")) {
                    TagKey<Block> tag = TagKey.of(RegistryKeys.BLOCK, Identifier.of(entry.substring(1)));
                    for (RegistryEntry<Block> block : Registries.BLOCK.iterateEntries(tag)) add(blocks, block.value(), entry);
                } else {
                    Identifier id = Identifier.of(entry);
                    Registries.BLOCK.getOptionalValue(id).ifPresentOrElse(
                            block -> add(blocks, block, entry),
                            () -> LOGGER.warn("Unknown block '{}' in {}", entry, FILE));
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Invalid entry '{}' in {}: {}", entry, FILE, e.getMessage());
            }
        }
        Switchables.setConfigBlocks(blocks);
        if (!blocks.isEmpty()) LOGGER.info("{} extra switchable block(s) from {}", blocks.size(), FILE);
    }

    private static void add(Set<Block> blocks, Block block, String entry) {
        if (block.getDefaultState().hasBlockEntity()) {
            LOGGER.warn("'{}' (from '{}') has a block entity and cannot be switchable, ignored", Registries.BLOCK.getId(block), entry);
            return;
        }
        blocks.add(block);
    }

    private static List<String> readEntries() {
        try {
            if (!Files.exists(FILE)) {
                writeDefault();
                return List.of();
            }
            JsonObject root = GSON.fromJson(Files.readString(FILE), JsonObject.class);
            JsonArray array = root == null ? null : root.getAsJsonArray(KEY);
            if (array == null) return List.of();
            return array.asList().stream().map(JsonElement::getAsString).map(String::trim).filter(s -> !s.isEmpty()).toList();
        } catch (IOException | JsonParseException | IllegalStateException | ClassCastException e) {
            LOGGER.error("Could not read {}: {}", FILE, e.getMessage());
            return List.of();
        }
    }

    private static void writeDefault() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Extra blocks the hop switch can make disappear: block ids (\"minecraft:stone\") "
                + "or block tags (\"#minecraft:wool\"). Blocks with a block entity (chests...) are ignored. "
                + "Applied on server start and /reload. Datapacks can also add to the tag steveparty:switchable.");
        root.add(KEY, new JsonArray());
        Files.createDirectories(FILE.getParent());
        Files.writeString(FILE, GSON.toJson(root));
    }

    private static SwitchableBlocksPayload payload() {
        return new SwitchableBlocksPayload(Switchables.getConfigBlocks().stream().map(Registries.BLOCK::getId).toList());
    }

    private static void send(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, payload());
    }

    private static void sendToAll(MinecraftServer server) {
        SwitchableBlocksPayload payload = payload();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) ServerPlayNetworking.send(player, payload);
    }
}
