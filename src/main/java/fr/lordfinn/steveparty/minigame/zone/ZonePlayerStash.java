package fr.lordfinn.steveparty.minigame.zone;

import fr.lordfinn.steveparty.mixin.PlayerShoulderInvoker;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;

import java.util.UUID;

/**
 * What a player owns and leaves at the door of a session: inventory (armour, off hand, what the cursor and the
 * crafting grid held), ender chest, experience, selected slot and game mode. Taken as NBT when the player comes in,
 * given back when it leaves; in between the player holds a session inventory, empty at first, destroyed at the end.
 */
final class ZonePlayerStash {
    /**
     * Worn by a player while it holds a session inventory, and saved with it: after a crash, a player wearing it was
     * saved during its session (its real inventory is the stashed one), one without it was saved before (its
     * inventory is the real one: the stash is from a time the server never saved).
     */
    static final String TAG = "steveparty.zone_bubble";

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
        return nbt;
    }

    /** The player owns nothing any more: the session inventory it starts with. */
    static void empty(ServerPlayerEntity player) {
        player.getInventory().clear();
        player.getEnderChestInventory().clear();
        setExperience(player, 0, 0, 0);
        player.addCommandTag(TAG);
        player.currentScreenHandler.syncState();
    }

    /** The session inventory of the player is destroyed, and what it owned given back. */
    static void restore(ServerPlayerEntity player, NbtCompound stash) {
        // nothing of the session follows the player: neither what its cursor or its crafting grid hold, nor a
        // parrot of the zone on its shoulder
        player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
        player.playerScreenHandler.getCraftingInput().clear();
        player.closeHandledScreen();
        ((PlayerShoulderInvoker) player).steveparty$setShoulderEntityLeft(new NbtCompound());
        ((PlayerShoulderInvoker) player).steveparty$setShoulderEntityRight(new NbtCompound());

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.readNbt(stash.getList("Inventory", NbtElement.COMPOUND_TYPE));
        player.getEnderChestInventory().readNbtList(stash.getList("EnderItems", NbtElement.COMPOUND_TYPE), player.getRegistryManager());
        inventory.selectedSlot = MathHelper.clamp(stash.getInt("SelectedSlot"), 0, PlayerInventory.getHotbarSize() - 1);
        if (player.networkHandler != null) player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(inventory.selectedSlot));
        setExperience(player, stash.getInt("XpLevel"), stash.getFloat("XpProgress"), stash.getInt("XpTotal"));
        GameMode mode = GameMode.byId(stash.getInt("GameMode"));
        if (player.interactionManager.getGameMode() != mode) player.changeGameMode(mode);
        player.removeCommandTag(TAG);
        player.currentScreenHandler.syncState();
    }

    private static void setExperience(ServerPlayerEntity player, int level, float progress, int total) {
        player.experienceLevel = level;
        player.experienceProgress = progress;
        player.totalExperience = total;
        // sent again to its client
        player.addExperienceLevels(0);
    }
}
