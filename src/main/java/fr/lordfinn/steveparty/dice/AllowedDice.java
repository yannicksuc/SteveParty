package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.items.custom.DefaultDiceItem;
import fr.lordfinn.steveparty.powerups.PowerUpLimit;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The party's « Restrict dice » setting ({@link PartyControllerEntity#isRestrictDice}, off by default): when on, a player
 * of the running party may only throw one of the dice the board's creator lists ({@link PartyControllerEntity#getAllowedDice},
 * {@link #defaults} at first); an empty list allows every die.
 * <p>
 * « The same die »: the same item (plain, double, triple die) with the same forged faces ({@link DiceFacesComponent})
 * and the same modules ({@link DiceModulesComponent}); its count, name or anything else does not matter. Server side.
 */
public final class AllowedDice {
    private AllowedDice() {
    }

    /** The list a new controller starts with: the plain Default Die (no faces, no modules). */
    public static List<ItemStack> defaults() {
        // The Default Die and its power-up version (carrying the Power-up Module)
        ItemStack die = new ItemStack(fr.lordfinn.steveparty.items.ModItems.DEFAULT_DICE);
        return List.of(die, DiceModules.set(die.copy(), java.util.Map.of(DiceModules.POWER_UP, 1)));
    }

    /** True if {@code stack} is a die (plain, double, triple, forged or not). */
    public static boolean isDie(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof DefaultDiceItem;
    }

    /** True if {@code a} and {@code b} are the same die: same item, same faces, same modules. */
    public static boolean sameDie(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (!isDie(a) || !isDie(b) || a.getItem() != b.getItem()) return false;
        return Objects.equals(a.get(DiceFacesComponent.TYPE), b.get(DiceFacesComponent.TYPE))
                && Objects.equals(a.get(DiceModulesComponent.TYPE), b.get(DiceModulesComponent.TYPE));
    }

    /** True if {@code die} may be thrown under {@code allowed}: the list is empty, or it holds the same die. */
    public static boolean allows(List<ItemStack> allowed, ItemStack die) {
        if (allowed.isEmpty()) return true;
        for (ItemStack stack : allowed) {
            if (sameDie(stack, die)) return true;
        }
        return false;
    }

    /**
     * Whether {@code player} must be refused throwing {@code die}: they play in a running party restricting the dice
     * whose list does not hold it. If so, they are told why (action bar). Outside a party, every die may be thrown.
     */
    public static boolean refusesThrow(PlayerEntity player, ItemStack die) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return false;
        PartyControllerEntity party = PowerUpLimit.partyOf(player.getUuid());
        if (party == null || !party.isRestrictDice() || allows(party.getAllowedDice(), die)) return false;
        MessageUtils.sendToPlayer(serverPlayer, Text.translatable("message.steveparty.dice.not_allowed").formatted(Formatting.RED),
                MessageUtils.MessageType.ACTION_BAR);
        serverPlayer.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6F, 0.8F);
        return true;
    }
}
