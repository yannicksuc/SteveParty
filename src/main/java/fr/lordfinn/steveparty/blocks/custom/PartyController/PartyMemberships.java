package fr.lordfinn.steveparty.blocks.custom.PartyController;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which party controller each player follows last (the one sending him its HUD), saved with the server (overworld).
 * A controller only knows its players while loaded: this tells {@code /steveparty leave} where to find the party of a
 * player whose controller is unloaded, and the login whether a remembered party is still there.
 */
public final class PartyMemberships extends PersistentState {
    private static final String ID = "steveparty_party_memberships";
    private static final Type<PartyMemberships> TYPE = new Type<>(PartyMemberships::new, PartyMemberships::fromNbt, null);

    private final Map<UUID, GlobalPos> followed = new HashMap<>();

    public static PartyMemberships get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    /** The controller {@code player} follows, null if none is remembered. */
    public @Nullable GlobalPos of(UUID player) {
        return followed.get(player);
    }

    public void follow(UUID player, GlobalPos controller) {
        if (controller.equals(followed.put(player, controller))) return;
        markDirty();
    }

    /** {@code player} no longer follows the controller at {@code controller} (nothing if he follows another one). */
    void unfollow(UUID player, GlobalPos controller) {
        if (followed.remove(player, controller)) markDirty();
    }

    /** {@code player} follows no party any more. */
    public void forget(UUID player) {
        if (followed.remove(player) != null) markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        followed.forEach((player, at) -> {
            NbtCompound entry = new NbtCompound();
            entry.putString("Dimension", at.dimension().getValue().toString());
            entry.putLong("Pos", at.pos().asLong());
            nbt.put(player.toString(), entry);
        });
        return nbt;
    }

    public static PartyMemberships fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        PartyMemberships memberships = new PartyMemberships();
        for (String key : nbt.getKeys()) {
            NbtCompound entry = nbt.getCompound(key);
            Identifier dimension = Identifier.tryParse(entry.getString("Dimension"));
            if (dimension == null) continue;
            try {
                memberships.followed.put(UUID.fromString(key), GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension),
                        BlockPos.fromLong(entry.getLong("Pos"))));
            } catch (IllegalArgumentException ignored) {
                // Not a player id: skipped
            }
        }
        return memberships;
    }
}
