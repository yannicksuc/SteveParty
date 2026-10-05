package fr.lordfinn.steveparty.blocks.switchable;

import fr.lordfinn.steveparty.config.ServerConfig;
import fr.lordfinn.steveparty.payloads.custom.SwitchableBlocksPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The extra switchable blocks of the server: {@code "switchableBlocks"} of {@code config/steveparty.json}
 * ({@link ServerConfig}), block ids ({@code "minecraft:stone"}) or block tags ({@code "#minecraft:wool"}). Blocks with
 * a block entity are ignored. Read when the server starts and on {@code /reload}, then sent to the players (item
 * tooltips).
 */
public final class SwitchableConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("steveparty/switchable");
    private static final String FILE = "config/steveparty.json (switchableBlocks)";

    private SwitchableConfig() {
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerConfig.reloadSwitchableBlocks();
            load();
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            ServerConfig.reloadSwitchableBlocks();
            load();
            sendToAll(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
    }

    private static void load() {
        Set<Block> blocks = new LinkedHashSet<>();
        for (String entry : entries()) {
            try {
                if (entry.startsWith("#")) {
                    TagKey<Block> tag = TagKey.of(RegistryKeys.BLOCK, Identifier.of(entry.substring(1)));
                    for (RegistryEntry<Block> block : Registries.BLOCK.iterateEntries(tag)) add(blocks, block.value(), entry);
                } else {
                    Identifier id = Identifier.of(entry);
                    Registries.BLOCK.getOrEmpty(id).ifPresentOrElse(
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

    /** The entries of the settings, trimmed, blank ones left out. */
    private static List<String> entries() {
        List<String> entries = ServerConfig.get().switchableBlocks;
        if (entries == null) return List.of();
        return entries.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
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
