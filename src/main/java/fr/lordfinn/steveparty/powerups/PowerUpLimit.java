package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.items.custom.DefaultDiceItem;
import fr.lordfinn.steveparty.items.custom.PowerUpItem;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * How many power-ups a player of a running party may carry, like the three items of Mario Party: the party's
 * « Max power-ups » setting ({@link PartyControllerEntity#getMaxPowerUps}, 3 by default, 0 for no limit).
 * <p>
 * What counts, by the number of items of each stack: every power-up item ({@link PowerUpItem}), and every die
 * without the Infinity module (plain, double, triple or forged): it replaces the roll and is spent. A die with
 * Infinity comes back to its roller, its permanent die: it does not count.
 * <p>
 * Enforced on what a party gives during it: a purchase (Trading Stall through a Boxed Trader, a Shop space) is
 * refused with nothing paid, an Inventory space leaves the extra in its chest, a power-up on the ground is not picked
 * up past the limit. Outside a party, no limit. Server side.
 */
public final class PowerUpLimit {
    /** The setting's default: three, as in Mario Party. */
    public static final int DEFAULT = 3;
    /** The highest the setting goes (0 being no limit). */
    public static final int MAX = 20;

    private PowerUpLimit() {
    }

    /** True if {@code stack} counts as a power-up for the limit: a power-up item, or a die without Infinity. */
    public static boolean counts(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof PowerUpItem) return true;
        return stack.getItem() instanceof DefaultDiceItem && !DiceModules.has(stack, DiceModules.INFINITY);
    }

    /** The power-ups {@code player} carries (inventory, armour, off hand and the stack held by the cursor), by items. */
    public static int carried(PlayerEntity player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (counts(stack)) count += stack.getCount();
        }
        ItemStack cursor = player.currentScreenHandler == null ? ItemStack.EMPTY : player.currentScreenHandler.getCursorStack();
        if (counts(cursor)) count += cursor.getCount();
        return count;
    }

    /** The running party {@code player} plays a token in, null if none. */
    public static @Nullable PartyControllerEntity partyOf(@Nullable UUID player) {
        if (player == null) return null;
        for (PartyControllerEntity controller : PartyControllerEntity.getActivePartyControllers()) {
            if (controller.isRemoved() || !controller.getPartyData().isStarted()) continue;
            for (UUID token : controller.getPartyData().getTokens()) {
                if (controller.isTokenOwnedBy(token, player)) return controller;
            }
        }
        return null;
    }

    /** The limit for {@code player}: their party's setting, 0 (no limit) outside a party. */
    public static int limitOf(PlayerEntity player) {
        if (player.getWorld().isClient) return 0;
        PartyControllerEntity party = partyOf(player.getUuid());
        return party == null ? 0 : party.getMaxPowerUps();
    }

    /** How many more power-ups {@code player} may take ({@link Integer#MAX_VALUE} with no limit). */
    public static int room(PlayerEntity player) {
        int limit = limitOf(player);
        return limit <= 0 ? Integer.MAX_VALUE : Math.max(0, limit - carried(player));
    }

    /**
     * How many of {@code stack} {@code player} may take: all of it if it does not count or they have room for it,
     * else what is left under the limit.
     */
    public static int allowed(PlayerEntity player, ItemStack stack) {
        return counts(stack) ? Math.min(stack.getCount(), room(player)) : stack.getCount();
    }

    /**
     * Whether {@code player} must be refused {@code stack} whole (a purchase): it counts and goes over the limit. If
     * so, they are told why (action bar).
     */
    public static boolean refuses(PlayerEntity player, ItemStack stack) {
        if (allowed(player, stack) >= stack.getCount()) return false;
        tellFull(player);
        return true;
    }

    /** Tells {@code player} they carry as many power-ups as their party allows. */
    public static void tellFull(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        MessageUtils.sendToPlayer(serverPlayer, Text.translatable("message.steveparty.powerup.limit", carried(player),
                limitOf(player)).formatted(Formatting.RED), MessageUtils.MessageType.ACTION_BAR);
        serverPlayer.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.5F, 0.8F);
    }
}
