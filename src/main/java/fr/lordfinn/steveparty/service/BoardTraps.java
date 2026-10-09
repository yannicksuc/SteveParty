package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TrapSetComponent;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.TrapCartridgeItem;
import fr.lordfinn.steveparty.powerups.effects.PowerUpProtection;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.utils.MessageUtils;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The traps of the Trap cartridges ({@link TrapCartridgeItem}), during a party:
 * <ul>
 *     <li>a token stopping on a Trap space with a trap set by another player springs it ({@link #spring}): the
 *     cartridge's effect, for the one who set it (coins or an item stolen for them, spaces back, the next turn lost);
 *     a Padlock disarms it; the trap is then gone;</li>
 *     <li>the one who set it, or another token of the same player, never springs it (there is no team on the board:
 *     a player's tokens are their team);</li>
 *     <li>a token stopping on a Trap space where nothing sprang, its player holding a Trap item, is asked whether to
 *     set it there ({@link #offer}): only on the space its own token stopped on; one trap per space, a new one
 *     refused on a trapped space unless the cartridge lets it replace it.</li>
 * </ul>
 * A coin or item theft waits while the setter or the victim is offline (the trap stays). The coins taken never go
 * through a Common pot. The traps are kept in their cartridges (saved with their tiles). Server thread only.
 */
public final class BoardTraps {
    /** What a token stopping on a Trap space did to its trap. */
    public enum Outcome { NONE, OWN, WAITING, DISARMED, SPRUNG }

    /** Tokens whose player is asked whether to set a trap. */
    private static final Set<UUID> ASKING = ServerMemory.forgetOnStop(new HashSet<>());
    /** The last outcome on each Trap space, for its Router. */
    private static final Map<GlobalPos, Outcome> LAST = ServerMemory.forgetOnStop(new HashMap<>());

    private BoardTraps() {
    }

    public static @Nullable ItemStack trapCartridge(BoardSpaceBlockEntity space) {
        ItemStack stack = space.getActiveCartridgeItemStack();
        return stack.getItem() instanceof TrapCartridgeItem ? stack : null;
    }

    public static @Nullable TrapSetComponent trapAt(BoardSpaceBlockEntity space) {
        ItemStack cartridge = trapCartridge(space);
        return cartridge == null ? null : cartridge.get(ModComponents.TRAP_SET);
    }

    public static boolean isAsking(MobEntity token) {
        return ASKING.contains(token.getUuid());
    }

    public static Outcome lastOutcome(ServerWorld world, BlockPos space) {
        return LAST.getOrDefault(GlobalPos.create(world.getRegistryKey(), space), Outcome.NONE);
    }

    private static @Nullable UUID ownerOf(MobEntity token) {
        return token instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenOwner() : null;
    }

    private static @Nullable ServerPlayerEntity online(ServerWorld world, @Nullable UUID player) {
        return player == null ? null : world.getServer().getPlayerManager().getPlayer(player);
    }

    // ---------------------------------------------------------------- springing

    /** {@code token} (of {@code party}) stopped on {@code space}: springs the trap there if another player set it. */
    public static Outcome spring(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token, PartyControllerEntity party) {
        Outcome outcome = spring0(world, space, token, party);
        LAST.put(GlobalPos.create(world.getRegistryKey(), space.getPos().toImmutable()), outcome);
        return outcome;
    }

    private static Outcome spring0(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token, PartyControllerEntity party) {
        ItemStack cartridge = trapCartridge(space);
        TrapSetComponent trap = cartridge == null ? null : cartridge.get(ModComponents.TRAP_SET);
        if (trap == null) return Outcome.NONE;
        UUID victimId = ownerOf(token);
        if (trap.isOwnedBy(victimId, token.getUuid())) return Outcome.OWN;
        TrapCartridgeItem.Effect effect = TrapCartridgeItem.effect(cartridge);
        ServerPlayerEntity setter = online(world, trap.owner());
        ServerPlayerEntity victim = online(world, victimId);
        boolean theft = effect == TrapCartridgeItem.Effect.COINS || effect == TrapCartridgeItem.Effect.STEAL_ITEM;
        if (theft && (setter == null || victim == null)) return Outcome.WAITING;

        Vec3d at = BoardSpaces.standPos(world, space.getPos());
        cartridge.remove(ModComponents.TRAP_SET);
        space.update();
        if (PowerUpProtection.consume(party, token.getUuid(), PowerUpProtection.Attack.TRAP)) {
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, 1F, 1.4F);
            world.spawnParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 8, 0.3, 0.1, 0.3, 0.01);
            return Outcome.DISARMED;
        }
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_TRIPWIRE_CLICK_ON, SoundCategory.PLAYERS, 1F, 0.6F);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.PLAYERS, 0.8F, 1.2F);
        world.spawnParticles(ParticleTypes.CRIT, at.x, at.y + 0.3, at.z, 20, 0.35, 0.3, 0.35, 0.2);
        Text victimName = token.getDisplayName();
        Text setterName = setter != null ? setter.getDisplayName() : Text.translatable("message.steveparty.trap.someone");
        int amount = TrapCartridgeItem.amount(cartridge);
        Text message = switch (effect) {
            case COINS -> {
                ItemStack coin = party.getCurrency(PartyCurrency.COIN);
                int taken = InventoryUtils.take(victim.getInventory(), coin, amount);
                if (taken > 0) InventoryUtils.giveOrDrop(setter, coin, taken);
                yield Text.translatable("message.steveparty.trap.coins", victimName, setterName, taken);
            }
            case BACK -> {
                int back = AdvanceBackMoves.launch(world, token, space.getPos(), -amount);
                yield Text.translatable("message.steveparty.trap.back", victimName, back);
            }
            case SKIP_TURN -> {
                party.getPartyData().getSkippedTokens().add(token.getUuid());
                party.markDirty();
                yield Text.translatable("message.steveparty.trap.skip", victimName);
            }
            case STEAL_ITEM -> {
                ItemStack stolen = stealItem(world, victim, party);
                if (!stolen.isEmpty()) setter.getInventory().offerOrDrop(stolen);
                yield stolen.isEmpty() ? Text.translatable("message.steveparty.trap.nothing", victimName)
                        : Text.translatable("message.steveparty.trap.item", victimName, setterName, stolen.toHoverableText());
            }
        };
        MessageUtils.sendToNearby(world, at, PartyControllerEntity.PARTY_AUDIENCE_RADIUS, message.copy().formatted(Formatting.RED),
                MessageUtils.MessageType.CHAT);
        return Outcome.SPRUNG;
    }

    /** One item (not the party's coins or stars) of the victim's inventory, empty if none. */
    private static ItemStack stealItem(ServerWorld world, ServerPlayerEntity victim, PartyControllerEntity party) {
        ItemStack coin = party.getCurrency(PartyCurrency.COIN), star = party.getCurrency(PartyCurrency.STAR);
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < victim.getInventory().main.size(); slot++) {
            ItemStack stack = victim.getInventory().main.get(slot);
            if (stack.isEmpty() || ItemStack.areItemsAndComponentsEqual(stack, coin) || ItemStack.areItemsAndComponentsEqual(stack, star)) continue;
            slots.add(slot);
        }
        if (slots.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = victim.getInventory().main.get(slots.get(world.random.nextInt(slots.size()))).split(1);
        victim.getInventory().markDirty();
        return taken;
    }

    // ---------------------------------------------------------------- setting

    /** The token's player may set a trap on {@code space} now: they hold a Trap, and the space takes one. */
    public static boolean canSet(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token) {
        ItemStack cartridge = trapCartridge(space);
        ServerPlayerEntity player = online(world, ownerOf(token));
        if (cartridge == null || player == null || player.getInventory().count(ModItems.BOARD_TRAP) == 0) return false;
        return cartridge.get(ModComponents.TRAP_SET) == null || TrapCartridgeItem.replaces(cartridge);
    }

    /**
     * Asks the token's player whether to set a Trap on {@code space}; {@code then} runs once they answered (or the
     * time ran out: no trap). False (nothing asked, {@code then} not run) if they can't set one.
     */
    public static boolean offer(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token, Runnable then) {
        if (!canSet(world, space, token)) return false;
        ServerPlayerEntity player = online(world, ownerOf(token));
        ASKING.add(token.getUuid());
        List<DicePrompts.Option> options = List.of(
                new DicePrompts.Option(new ItemStack(ModItems.BOARD_TRAP), Text.translatable("gui.steveparty.trap.set").formatted(Formatting.WHITE)),
                new DicePrompts.Option(new ItemStack(Items.BARRIER), Text.translatable("gui.steveparty.trap.no").formatted(Formatting.WHITE)));
        DicePrompts.Prompt prompt = DicePrompts.ask(player, Text.translatable("gui.steveparty.trap.title"), DicePrompts.Layout.LIST,
                options, DicePrompts.TIMEOUT_TICKS, 1, index -> {
                    ASKING.remove(token.getUuid());
                    if (index == 0) set(world, space, token);
                    then.run();
                });
        if (prompt == null) {
            ASKING.remove(token.getUuid());
            return false;
        }
        return true;
    }

    /** Sets a trap of the token's player on {@code space}, using up one of their Traps (not in creative). */
    public static boolean set(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token) {
        if (!canSet(world, space, token)) return false;
        ItemStack cartridge = trapCartridge(space);
        ServerPlayerEntity player = online(world, ownerOf(token));
        if (!player.isInCreativeMode()) InventoryUtils.take(player.getInventory(), new ItemStack(ModItems.BOARD_TRAP), 1);
        cartridge.set(ModComponents.TRAP_SET, new TrapSetComponent(player.getUuid(), token.getUuid(), colorOf(token, player.getUuid())));
        space.update();
        Vec3d at = BoardSpaces.standPos(world, space.getPos());
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_TRIPWIRE_ATTACH, SoundCategory.PLAYERS, 0.8F, 0.8F);
        MessageUtils.sendToNearby(world, at, PartyControllerEntity.PARTY_AUDIENCE_RADIUS,
                Text.translatable("message.steveparty.trap.set", player.getDisplayName()).formatted(Formatting.DARK_RED),
                MessageUtils.MessageType.ACTION_BAR);
        return true;
    }

    /** The plate's colour: the token's colour, else a dye colour given by its player. */
    private static int colorOf(MobEntity token, UUID player) {
        int color = token instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenColor() : -1;
        if (color >= 0) return color & 0xFFFFFF;
        DyeColor dye = DyeColor.values()[Math.floorMod(player.hashCode(), DyeColor.values().length)];
        return dye.getEntityColor();
    }
}
