package fr.lordfinn.steveparty.minigame.zone;

import fr.lordfinn.steveparty.mixin.PlayerShoulderInvoker;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * What a player owns and leaves at the door of a session: inventory (armour, off hand, what the cursor and the
 * crafting grid held), ender chest, experience, selected slot, game mode, status effects (paused meanwhile: a potion
 * drunk before does not help in the round, one drunk in the round does not follow out of it) and spawn point (a bed
 * slept in during the round does not move it). Health and hunger are not: the round is played in the player's body. Taken as NBT when the player comes in,
 * given back when it leaves; in between the player holds a session inventory, empty at first, destroyed at the end.
 */
final class ZonePlayerStash {
    /**
     * Worn by a player while it holds a session inventory, and saved with it: after a crash, a player wearing it was
     * saved during its session (its real inventory is the stashed one), one without it was saved before (its
     * inventory is the real one: the stash is from a time the server never saved).
     */
    static final String TAG = "steveparty.zone_bubble";
    /**
     * Worn by a player given back what it owns while on its death screen: its body holds it, and the player it
     * respawns as takes it over ({@link #afterRespawn}), saved with it in between (it may log out, the server may stop).
     */
    static final String DEAD_TAG = "steveparty.zone_bubble.dead";

    private ZonePlayerStash() {
    }

    /** What the player owns right now. The cursor and crafting grid are folded into its inventory first. */
    static NbtCompound capture(ServerPlayerEntity player, UUID session) {
        // the cursor stack and the crafting grid go back to the inventory (dropped at its feet if it is full, where
        // they lie like any item: before the zone's entities are remembered)
        player.closeHandledScreen();
        // a parrot riding along steps down: it would be lost with the session otherwise
        ((PlayerShoulderInvoker) player).steveparty$dropShoulderEntities();
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("Session", session);
        nbt.put("Inventory", player.getInventory().writeNbt(new NbtList()));
        nbt.put("EnderItems", player.getEnderChestInventory().toNbtList(player.getRegistryManager()));
        nbt.putInt("SelectedSlot", player.getInventory().selectedSlot);
        nbt.putInt("XpLevel", player.experienceLevel);
        nbt.putFloat("XpProgress", player.experienceProgress);
        nbt.putInt("XpTotal", player.totalExperience);
        nbt.putInt("GameMode", player.interactionManager.getGameMode().getId());
        NbtList effects = new NbtList();
        for (StatusEffectInstance effect : player.getStatusEffects()) effects.add(effect.writeNbt());
        nbt.put("Effects", effects);
        BlockPos spawn = player.getSpawnPointPosition();
        nbt.putBoolean("HasSpawn", spawn != null);
        if (spawn != null) {
            nbt.putString("SpawnDimension", player.getSpawnPointDimension().getValue().toString());
            nbt.putLong("SpawnPos", spawn.asLong());
            nbt.putFloat("SpawnAngle", player.getSpawnAngle());
            nbt.putBoolean("SpawnForced", player.isSpawnForced());
        }
        return nbt;
    }

    /** The player owns nothing any more: the session inventory it starts with. */
    static void empty(ServerPlayerEntity player) {
        player.getInventory().clear();
        player.getEnderChestInventory().clear();
        setExperience(player, 0, 0, 0);
        player.clearStatusEffects();
        player.addCommandTag(TAG);
        player.currentScreenHandler.syncState();
    }

    /** The session inventory of the player is destroyed, and what it owned given back. */
    static void restore(ServerPlayerEntity player, NbtCompound stash) {
        // nothing of the session follows the player: neither what its cursor, its crafting grid or the inputs of the
        // screen it has open (a crafting table, an anvil...) hold, which would be dropped where it stands if its
        // inventory is full, nor a parrot of the zone on its shoulder
        ZoneBorder.discardingDrops(() -> {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            player.playerScreenHandler.getCraftingInput().clear();
            player.closeHandledScreen();
        });
        ((PlayerShoulderInvoker) player).steveparty$setShoulderEntityLeft(new NbtCompound());
        ((PlayerShoulderInvoker) player).steveparty$setShoulderEntityRight(new NbtCompound());

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.readNbt(stash.getList("Inventory", NbtElement.COMPOUND_TYPE));
        player.getEnderChestInventory().readNbtList(stash.getList("EnderItems", NbtElement.COMPOUND_TYPE), player.getRegistryManager());
        inventory.selectedSlot = MathHelper.clamp(stash.getInt("SelectedSlot"), 0, PlayerInventory.getHotbarSize() - 1);
        if (player.networkHandler != null) player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(inventory.selectedSlot));
        setExperience(player, stash.getInt("XpLevel"), stash.getFloat("XpProgress"), stash.getInt("XpTotal"));
        player.clearStatusEffects();
        for (NbtElement element : stash.getList("Effects", NbtElement.COMPOUND_TYPE)) {
            StatusEffectInstance effect = StatusEffectInstance.fromNbt((NbtCompound) element);
            if (effect != null) player.addStatusEffect(effect);
        }
        if (stash.contains("HasSpawn")) restoreSpawn(player, stash);
        GameMode mode = GameMode.byId(stash.getInt("GameMode"));
        if (player.interactionManager.getGameMode() != mode) player.changeGameMode(mode);
        player.removeCommandTag(TAG);
        // dead: the player it respawns as would leave all of it with its body (keepInventory off)
        if (player.isDead()) player.addCommandTag(DEAD_TAG);
        player.currentScreenHandler.syncState();
    }

    /** A player given back what it owns on its death screen respawns: it takes it from its body. */
    static void afterRespawn(ServerPlayerEntity body, ServerPlayerEntity player) {
        if (!body.getCommandTags().contains(DEAD_TAG)) return;
        player.removeCommandTag(DEAD_TAG);
        player.getInventory().clone(body.getInventory());
        setExperience(player, body.experienceLevel, body.experienceProgress, body.totalExperience);
        for (StatusEffectInstance effect : body.getStatusEffects()) player.addStatusEffect(new StatusEffectInstance(effect));
        player.currentScreenHandler.syncState();
    }

    private static void restoreSpawn(ServerPlayerEntity player, NbtCompound stash) {
        Identifier dimension = Identifier.tryParse(stash.getString("SpawnDimension"));
        if (!stash.getBoolean("HasSpawn") || dimension == null) {
            player.setSpawnPoint(World.OVERWORLD, null, 0, false, false);
            return;
        }
        player.setSpawnPoint(RegistryKey.of(RegistryKeys.WORLD, dimension), BlockPos.fromLong(stash.getLong("SpawnPos")),
                stash.getFloat("SpawnAngle"), stash.getBoolean("SpawnForced"), false);
    }

    /** The player dies where what it drops is destroyed: its session experience goes too, it drops no orb. */
    static void dropNoExperience(ServerPlayerEntity player) {
        player.experienceLevel = 0;
        player.experienceProgress = 0;
        player.totalExperience = 0;
    }

    private static void setExperience(ServerPlayerEntity player, int level, float progress, int total) {
        player.experienceLevel = level;
        player.experienceProgress = progress;
        player.totalExperience = total;
        // sent again to its client
        player.addExperienceLevels(0);
    }
}
