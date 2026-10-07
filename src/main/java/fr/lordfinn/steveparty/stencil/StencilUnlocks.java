package fr.lordfinn.steveparty.stencil;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The built-in stencil patterns ({@link StencilPatterns}) a player unlocked in survival, by id: a pattern is
 * unlocked when a stencil cut to it goes through their inventory (found in a chest, given by a friend...), see
 * {@link StencilLibrary#learnFromInventory}. Kept apart from the library, so removing a pattern from the library
 * never locks it again. The {@link StencilPatterns.Pattern#free() free} patterns are always unlocked.
 * <p>
 * Having unlocked every pattern grants the {@link #ADVANCEMENT} challenge. Ids of patterns no longer in the
 * library are kept but ignored.
 */
public record StencilUnlocks(Set<String> ids) {
    public static final StencilUnlocks EMPTY = new StencilUnlocks(Set.of());
    public static final Identifier ADVANCEMENT = Steveparty.id("workshop/signs/all_stencil_patterns");
    private static final String CRITERION = "unlocked_all";

    public static final Codec<StencilUnlocks> CODEC = Codec.STRING.listOf().xmap(
            list -> new StencilUnlocks(Set.copyOf(list)), unlocks -> unlocks.ids.stream().sorted().toList());
    public static final PacketCodec<ByteBuf, StencilUnlocks> PACKET_CODEC = PacketCodecs.STRING
            .collect(PacketCodecs.toList()).xmap(list -> new StencilUnlocks(Set.copyOf(list)), unlocks -> List.copyOf(unlocks.ids));

    public static final AttachmentType<StencilUnlocks> TYPE = AttachmentRegistry.<StencilUnlocks>builder()
            .persistent(CODEC)
            .copyOnDeath()
            .initializer(() -> EMPTY)
            .syncWith(PACKET_CODEC, AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(Steveparty.id("stencil_unlocks"));

    public StencilUnlocks {
        ids = Set.copyOf(ids);
    }

    public static StencilUnlocks of(PlayerEntity player) {
        StencilUnlocks unlocks = player.getAttached(TYPE);
        return unlocks == null ? EMPTY : unlocks;
    }

    public boolean isUnlocked(StencilPatterns.Pattern pattern) {
        return pattern.free() || ids.contains(pattern.id());
    }

    /** @return true once every pattern to find in chests is unlocked (always against the current library). */
    public boolean hasAll() {
        return StencilPatterns.lockable().stream().allMatch(this::isUnlocked);
    }

    /** @return these unlocks with the built-in pattern of this shape, if it is a locked one; else this same instance. */
    public StencilUnlocks with(byte[] shape) {
        StencilPatterns.Pattern pattern = StencilPatterns.byShape(shape);
        if (pattern == null || isUnlocked(pattern)) return this;
        Set<String> set = new HashSet<>(ids);
        set.add(pattern.id());
        return new StencilUnlocks(set);
    }

    /** Stores the player's unlocks if they changed, and grants the challenge once they are complete. */
    static void update(ServerPlayerEntity player, StencilUnlocks previous, StencilUnlocks unlocks) {
        if (unlocks != previous) player.setAttached(TYPE, unlocks);
        if (!unlocks.hasAll()) return;
        @Nullable AdvancementEntry advancement = player.server.getAdvancementLoader().get(ADVANCEMENT);
        if (advancement != null && !player.getAdvancementTracker().getProgress(advancement).isDone()) {
            player.getAdvancementTracker().grantCriterion(advancement, CRITERION);
        }
    }
}
