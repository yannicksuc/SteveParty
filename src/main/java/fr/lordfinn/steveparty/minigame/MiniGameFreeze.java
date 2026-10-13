package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * The start of a mini-game round: everyone comes out of the pipes at once, then the players stand still for
 * {@value #SECONDS} seconds (they can't walk, jump, hit or use anything) while the title counts down; at « Go! » they
 * are free. So nobody is ahead because he came out of a pipe first.
 * <p>
 * The modifiers are temporary (never saved): a player who disconnects or a server that stops loses them.
 */
public final class MiniGameFreeze {
    public static final int SECONDS = 3;
    private static final Identifier MODIFIER = Steveparty.id("mini_game_freeze");
    private static final int FADE_IN = 0, STAY = 25, FADE_OUT = 5;

    /** A countdown: those it freezes, those who see it, the title over the numbers, and whether the round is still on. */
    private record Countdown(Set<UUID> frozen, Set<UUID> audience, Text title, int start, BooleanSupplier stillOn) {
    }

    private static final List<Countdown> RUNNING = new ArrayList<>();
    private static final Set<UUID> FROZEN = new HashSet<>();

    private MiniGameFreeze() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(MiniGameFreeze::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            for (Countdown countdown : RUNNING) countdown.frozen().remove(handler.player.getUuid());
            release(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            RUNNING.clear();
            FROZEN.clear();
        });
        // Frozen: no hit, no use (the server says no; the client may show the swing)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isFrozen(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) -> isFrozen(player)
                ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
    }

    public static boolean isFrozen(PlayerEntity player) {
        return !player.getWorld().isClient() && FROZEN.contains(player.getUuid());
    }

    /**
     * The round starts: {@code players} are frozen until « Go! », {@code audience} (the players too) see the countdown
     * under {@code name} (the mini-game's title, empty: the numbers alone).
     *
     * @param stillOn false once the round is over: the players are freed at once
     */
    public static void start(MinecraftServer server, Collection<ServerPlayerEntity> players, Collection<ServerPlayerEntity> audience,
                             String name, BooleanSupplier stillOn) {
        Set<UUID> frozen = new HashSet<>(), watching = new HashSet<>();
        for (ServerPlayerEntity player : players) {
            // A new round's countdown takes him from the one before (a practice round started again)
            for (Countdown before : RUNNING) before.frozen().remove(player.getUuid());
            frozen.add(player.getUuid());
            freeze(player);
        }
        for (ServerPlayerEntity player : audience) watching.add(player.getUuid());
        watching.addAll(frozen);
        Text title = name.isEmpty() ? Text.empty() : Text.literal(name).styled(style -> style.withColor(0xFFC52E).withBold(true));
        Countdown countdown = new Countdown(frozen, watching, title, server.getTicks(), stillOn);
        RUNNING.add(countdown);
        show(server, countdown, SECONDS);
    }

    private static void tick(MinecraftServer server) {
        if (RUNNING.isEmpty()) return;
        for (Countdown countdown : new ArrayList<>(RUNNING)) {
            int elapsed = server.getTicks() - countdown.start();
            if (!countdown.stillOn().getAsBoolean()) {
                end(server, countdown, false);
                continue;
            }
            if (elapsed <= 0 || elapsed % 20 != 0) continue;
            int left = SECONDS - elapsed / 20;
            if (left > 0) show(server, countdown, left);
            else end(server, countdown, true);
        }
    }

    /** The title with {@code left} under it (or alone without a title), and a tick of sound. */
    private static void show(MinecraftServer server, Countdown countdown, int left) {
        Text number = Text.literal(Integer.toString(left)).styled(style -> style.withColor(0xFFFFFF).withBold(true));
        for (UUID uuid : countdown.audience()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(FADE_IN, STAY, FADE_OUT));
            if (countdown.title().getString().isEmpty()) {
                player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.empty()));
                player.networkHandler.sendPacket(new TitleS2CPacket(number));
            } else {
                player.networkHandler.sendPacket(new SubtitleS2CPacket(number));
                player.networkHandler.sendPacket(new TitleS2CPacket(countdown.title()));
            }
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 1f, 1f);
        }
    }

    /** « Go! » (when it ran to its end) and everyone freed. */
    private static void end(MinecraftServer server, Countdown countdown, boolean go) {
        RUNNING.remove(countdown);
        Text goText = Text.translatableWithFallback("message.steveparty.minigame.go", "Go!").styled(style -> style.withColor(0x55FF55).withBold(true));
        for (UUID uuid : countdown.audience()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            if (countdown.frozen().contains(uuid)) release(player);
            if (!go) continue;
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(FADE_IN, 20, 10));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.empty()));
            player.networkHandler.sendPacket(new TitleS2CPacket(goText));
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1f, 1.4f);
        }
    }

    private static void freeze(ServerPlayerEntity player) {
        FROZEN.add(player.getUuid());
        for (RegistryEntry<EntityAttribute> attribute : List.of(EntityAttributes.GENERIC_MOVEMENT_SPEED, EntityAttributes.GENERIC_JUMP_STRENGTH)) {
            EntityAttributeInstance instance = player.getAttributeInstance(attribute);
            if (instance != null && instance.getModifier(MODIFIER) == null) {
                instance.addTemporaryModifier(new EntityAttributeModifier(MODIFIER, -1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    /** {@code player} may move again (nothing if he was not frozen). */
    public static void release(ServerPlayerEntity player) {
        FROZEN.remove(player.getUuid());
        for (RegistryEntry<EntityAttribute> attribute : List.of(EntityAttributes.GENERIC_MOVEMENT_SPEED, EntityAttributes.GENERIC_JUMP_STRENGTH)) {
            EntityAttributeInstance instance = player.getAttributeInstance(attribute);
            if (instance != null) instance.removeModifier(MODIFIER);
        }
    }
}
