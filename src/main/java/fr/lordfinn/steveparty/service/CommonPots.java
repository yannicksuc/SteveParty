package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.advancement.ModCriteria;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrencyText;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlock;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.PartyController.journal.JournalPlace;
import fr.lordfinn.steveparty.blocks.custom.PartyController.journal.TurnJournal;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.magpie.MagpieEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Common pots ({@link PotCartridgeItem}): the pot is the Magpie Nest its cartridge is linked to, the coins in it
 * real items. A token passing over a pot space puts its stake into the nest (all its player has if less, no debt; up
 * to the pot's cap), the token stopping exactly on it wins everything in the nest and the pot starts again. Coins
 * only: the party's coin (the mod's coin outside a party). Nothing is ever made: its base ({@link PotCartridgeItem#START})
 * is taken from the space's storage ({@link #storageOf}: the containers of a container cartridge of the space, else
 * the bank of the party running on its board) when a pot never filled is first used and right after each win; at
 * the party's end, its coins go back to that storage (what does not fit stays in the nest). No nest linked: the pot
 * does nothing. Several pots are independent: each cartridge links its own nest.
 * <p>
 * The Pie ({@link MagpieEntity}) lives on the nest: it flies to fetch the stakes and brings the pot to its winner.
 * Server thread only.
 */
public final class CommonPots {
    /** How often a pot space looks after its nest and its Pie. */
    public static final int CARE_INTERVAL = 40;
    private static final int FLIGHT_TICKS = 18;

    /** What a token passing gave the pot. */
    public record Stake(int coins) {
        public static final Stake NONE = new Stake(0);
    }

    /** What a winner took: the coins and the other things. */
    public record Win(int coins, List<ItemStack> items) {
        public static final Win NONE = new Win(0, List.of());

        public boolean isEmpty() {
            return coins == 0 && items.isEmpty();
        }
    }

    private CommonPots() {
    }

    // ---------------------------------------------------------------- the pot

    /** The Common pot cartridge of {@code space}, or null. */
    public static @Nullable ItemStack potOf(BoardSpaceBlockEntity space) {
        return space.getActiveCartridge(PotCartridgeItem.class);
    }

    /** The coin of the token's party, the mod's coin outside a party. */
    public static ItemStack coinOf(MobEntity token) {
        return PartyControllerEntity.getRunningPartyOf(token.getUuid())
                .map(party -> party.getCurrency(PartyCurrency.COIN))
                .orElseGet(() -> new ItemStack(ModItems.COIN));
    }

    /** The coin of {@code party}, the mod's coin for none. */
    public static ItemStack coinOf(@Nullable PartyControllerEntity party) {
        return party == null ? new ItemStack(ModItems.COIN) : party.getCurrency(PartyCurrency.COIN);
    }

    /** The player of the token, if connected. */
    public static @Nullable ServerPlayerEntity playerOf(ServerWorld world, MobEntity token) {
        UUID owner = token instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenOwner() : null;
        return owner == null ? null : world.getServer().getPlayerManager().getPlayer(owner);
    }

    /** The nest of the pot space {@code space} (its cartridge's, still a nest), or null. */
    public static @Nullable MagpieNestBlockEntity nestEntityOf(ServerWorld world, BoardSpaceBlockEntity space) {
        ItemStack pot = potOf(space);
        BlockPos nest = pot == null ? null : PotCartridgeItem.nest(pot, world);
        MagpieNestBlockEntity entity = nest == null ? null : MagpieNestBlockEntity.at(world, nest);
        if (entity != null) entity.link(space.getPos());
        return entity;
    }

    /**
     * Where the pot space takes its base from and puts its coins back at the party's end: the containers of a
     * container cartridge of the space (an Inventory Cartridge in another slot), else the bank of {@code party}; none
     * ({@link PartyResources#NONE}) without either.
     */
    public static PartyResources storageOf(ServerWorld world, BoardSpaceBlockEntity space, @Nullable PartyControllerEntity party) {
        for (int slot = 0; slot < space.size(); slot++) {
            ItemStack cartridge = space.getStack(slot);
            if (!CartridgeContainers.linksContainers(cartridge) || CartridgeContainers.isEmpty(cartridge)) continue;
            List<Inventory> containers = CartridgeContainers.available(cartridge, world);
            if (!containers.isEmpty()) return PartyResources.of(containers);
        }
        return PartyResources.of(party);
    }

    /**
     * {@code token} passes over the pot space: its player puts the stake into the nest (what they have, no debt, as
     * much as the cap and the nest let in). No nest: nothing, and the space says so.
     */
    public static Stake pass(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token) {
        ItemStack pot = potOf(space);
        ServerPlayerEntity player = playerOf(world, token);
        if (pot == null || player == null) return Stake.NONE;
        MagpieNestBlockEntity nest = nestEntityOf(world, space);
        Vec3d at = BoardSpaces.standPos(world, space.getPos());
        if (nest == null) {
            MessageUtils.sendToNearby(world, at, 100, Text.translatable("message.steveparty.pot.no_nest").formatted(Formatting.GRAY),
                    MessageUtils.MessageType.ACTION_BAR);
            return Stake.NONE;
        }
        PartyControllerEntity party = PartyControllerEntity.getRunningPartyOf(token.getUuid()).orElse(null);
        fillBase(world, space, party);
        Stake stake = collect(pot, nest, token, player);
        space.update();
        Text name = token.getDisplayName();
        ItemStack coin = coinOf(token);
        Text total = PartyCurrencyText.amount(coin, nest.count(coin));
        Text message = stake.coins() > 0
                ? Text.translatable("message.steveparty.pot.stake", name, PartyCurrencyText.amount(coin, stake.coins()), total)
                : Text.translatable("message.steveparty.pot.no_stake", name, total);
        MessageUtils.sendToNearby(world, at, 100, message.copy().formatted(Formatting.GOLD), MessageUtils.MessageType.ACTION_BAR);
        if (stake.coins() > 0) fetch(world, space, token, coinOf(token));
        return stake;
    }

    /**
     * Its base still due (a pot never filled, or won since) and a storage to take it from ({@link #storageOf}): the
     * nest is filled up to its base from it, as far as the storage, the cap and the nest allow, and the base is no
     * longer due. No nest or no storage: nothing (it stays due). @return the coins taken from the storage
     */
    public static int fillBase(ServerWorld world, BoardSpaceBlockEntity space, @Nullable PartyControllerEntity party) {
        ItemStack pot = potOf(space);
        if (pot == null || !PotCartridgeItem.baseDue(pot)) return 0;
        MagpieNestBlockEntity nest = nestEntityOf(world, space);
        PartyResources storage = storageOf(world, space, party);
        if (nest == null || storage.isNone()) return 0;
        ItemStack coin = coinOf(party);
        int wanted = Math.min(Math.max(0, PotCartridgeItem.start(pot) - nest.count(coin)), nest.coinRoom(coin));
        int taken = wanted > 0 ? storage.take(coin, wanted) : 0;
        int added = nest.addCoins(coin, taken);
        // What the nest would not take goes back where it came from
        if (added < taken) storage.give(coin.copyWithCount(taken - added));
        TurnJournal.moved(party, JournalPlace.of(storage), JournalPlace.Nest.of(nest), coin, added);
        TurnJournal.space(space);
        PotCartridgeItem.setBaseDue(pot, false);
        space.update();
        return added;
    }

    /** Takes the stake from {@code player} into the nest. */
    private static Stake collect(ItemStack pot, MagpieNestBlockEntity nest, MobEntity token, ServerPlayerEntity player) {
        ItemStack coin = coinOf(token);
        int wanted = Math.min(PotCartridgeItem.stake(pot), nest.coinRoom(coin));
        int taken = InventoryUtils.take(player.getInventory(), coin, wanted);
        int added = nest.addCoins(coin, taken);
        if (added < taken) InventoryUtils.giveOrDrop(player, coin, taken - added);
        TurnJournal.moved(null, JournalPlace.player(player), JournalPlace.Nest.of(nest), coin, added);
        return new Stake(added);
    }

    /**
     * {@code token} stopped exactly on the pot space: its player wins everything in the nest (paying a stake first if
     * the cartridge says so); the pot starts again from its base.
     */
    public static Win win(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token) {
        ItemStack pot = potOf(space);
        ServerPlayerEntity player = playerOf(world, token);
        if (pot == null || player == null) return Win.NONE;
        MagpieNestBlockEntity nest = nestEntityOf(world, space);
        Vec3d at = BoardSpaces.standPos(world, space.getPos());
        if (nest == null) {
            MessageUtils.sendToNearby(world, at, 100, Text.translatable("message.steveparty.pot.no_nest").formatted(Formatting.GRAY),
                    MessageUtils.MessageType.ACTION_BAR);
            return Win.NONE;
        }
        PartyControllerEntity party = PartyControllerEntity.getRunningPartyOf(token.getUuid()).orElse(null);
        fillBase(world, space, party);
        ItemStack coin = coinOf(token);
        int coins = 0;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : nest.takeAll()) {
            InventoryUtils.giveOrDrop(player, stack.copyWithCount(1), stack.getCount());
            TurnJournal.moved(party, JournalPlace.Nest.of(nest), JournalPlace.player(player), stack, stack.getCount());
            if (ItemStack.areItemsAndComponentsEqual(stack, coin)) coins += stack.getCount();
            else items.add(stack);
        }
        Win win = new Win(coins, items);
        // It starts again: its base from the storage, nothing made
        TurnJournal.space(space);
        PotCartridgeItem.setBaseDue(pot, true);
        fillBase(world, space, party);
        space.update();
        if (win.isEmpty()) {
            MessageUtils.sendToNearby(world, at, 100, Text.translatable("message.steveparty.pot.empty", token.getDisplayName())
                    .formatted(Formatting.GRAY), MessageUtils.MessageType.ACTION_BAR);
            return win;
        }
        int things = items.stream().mapToInt(ItemStack::getCount).sum();
        Text message = things > 0
                ? Text.translatable("message.steveparty.pot.won_items", player.getDisplayName(), PartyCurrencyText.amount(coin, coins), things)
                : Text.translatable("message.steveparty.pot.won", player.getDisplayName(), PartyCurrencyText.amount(coin, coins));
        MessageUtils.sendToNearby(world, at, 100, message.copy().formatted(Formatting.GOLD, Formatting.BOLD), MessageUtils.MessageType.CHAT);
        fanfare(world, at);
        deliver(world, space, token, coin);
        ModCriteria.fire(player, "pot_won", coins);
        return win;
    }

    /**
     * The party of {@code controller} is over: the coins of each pot of its board go back to the pot's storage
     * ({@link #storageOf}); what it cannot take stays in the nest. The pots' bases are due again.
     */
    public static void partyEnded(ServerWorld world, PartyControllerEntity controller) {
        for (BlockPos pos : controller.boardSpaces(world, BoardSpaceType.TILE_POT)) {
            if (world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity space) giveBack(world, space, controller);
        }
    }

    /**
     * The coins of the party {@code party} in the pot of {@code space} go back to its storage ({@link #storageOf});
     * what it cannot take stays in the nest. Its base is due again. @return how many went back
     */
    public static int giveBack(ServerWorld world, BoardSpaceBlockEntity space, @Nullable PartyControllerEntity party) {
        ItemStack pot = potOf(space);
        if (pot == null) return 0;
        PotCartridgeItem.setBaseDue(pot, true);
        MagpieNestBlockEntity nest = nestEntityOf(world, space);
        int given = nest == null ? 0 : emptyInto(nest, coinOf(party), storageOf(world, space, party));
        space.update();
        return given;
    }

    /** The coins like {@code coin} of {@code nest} into {@code storage}; what it does not take goes back into the nest. */
    private static int emptyInto(MagpieNestBlockEntity nest, ItemStack coin, PartyResources storage) {
        if (storage.isNone()) return 0;
        int count = nest.take(coin, nest.count(coin));
        if (count <= 0) return 0;
        ItemStack moving = coin.copyWithCount(count);
        int given = 0;
        while (!moving.isEmpty()) {
            ItemStack part = moving.split(moving.getMaxCount());
            int before = part.getCount();
            given += storage.give(part);
            if (part.getCount() == before) {
                moving.increment(part.getCount());
                break;
            }
            moving.increment(part.getCount());
        }
        if (!moving.isEmpty()) nest.addCoins(coin, moving.getCount());
        return given;
    }

    private static void fanfare(ServerWorld world, Vec3d at) {
        float[] pitches = {1.0F, 1.26F, 1.5F, 2.0F};
        for (int i = 0; i < pitches.length; i++) {
            float pitch = pitches[i];
            later(i * 3, () -> world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                    SoundCategory.BLOCKS, 0.7F, pitch));
        }
        later(10, () -> world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.6F, 1.4F));
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.6, at.z, 14, 0.4, 0.4, 0.4, 0.0);
    }

    private static void later(int ticks, Runnable task) {
        if (ticks <= 0) task.run();
        else Steveparty.SCHEDULER.schedule(UUID.randomUUID(), ticks, task);
    }

    // ---------------------------------------------------------------- the nest and the Pie

    /** The nest of the pot space at {@code space} (its cartridge's link, still a nest), or null. */
    public static @Nullable BlockPos nestOf(ServerWorld world, BlockPos space) {
        if (!(world.getBlockEntity(space) instanceof BoardSpaceBlockEntity entity)) return null;
        ItemStack pot = potOf(entity);
        return pot == null ? null : PotCartridgeItem.nest(pot, world);
    }

    private static List<MagpieEntity> magpiesAround(ServerWorld world, BlockPos nest) {
        return world.getEntitiesByType(ModEntities.MAGPIE, new Box(nest).expand(24), Entity::isAlive);
    }

    /** The Pie of the pot space at {@code space}, null if it has none (no nest, not spawned yet). */
    public static @Nullable MagpieEntity magpieOf(ServerWorld world, BlockPos space) {
        BlockPos nest = nestOf(world, space);
        if (nest == null) return null;
        for (MagpieEntity magpie : magpiesAround(world, nest)) {
            if (space.equals(magpie.getHome())) return magpie;
        }
        return null;
    }

    /**
     * Every {@link #CARE_INTERVAL} ticks, by the pot space: a party running on its board fills its base due from the
     * storage, its nest is told it is the pot, a nest without its Pie gets one. The Pie leaves by itself when the
     * cartridge or the nest goes (MagpieEntity).
     */
    public static void care(ServerWorld world, BoardSpaceBlockEntity space, ItemStack pot) {
        MagpieNestBlockEntity nest = nestEntityOf(world, space);
        if (nest == null) return;
        // A party running on its board: its base, still due, comes from the storage at once
        if (PotCartridgeItem.baseDue(pot)) {
            PartyControllerEntity party = PartyControllerEntity.getPartyOfBoardSpace(world, space.getPos(),
                    PartyControllerEntity.BOARD_NEARBY_RADIUS, false).orElse(null);
            if (party != null) fillBase(world, space, party);
        }
        if (magpieOf(world, space.getPos()) != null) return;
        MagpieEntity magpie = MagpieEntity.create(ModEntities.MAGPIE, world, space.getPos(), nest.getPos());
        // A hologram like every mob a space summons: never saved, the space gives it back after a reload
        BoardActors.mark(magpie);
        world.spawnEntity(magpie);
        world.spawnParticles(ParticleTypes.CLOUD, magpie.getX(), magpie.getY() + 0.3, magpie.getZ(), 5, 0.2, 0.2, 0.2, 0.01);
    }

    private static Vec3d above(Entity entity) {
        return entity.getPos().add(0, entity.getHeight() + 0.25, 0);
    }

    /** The Pie flies to the token, takes the stake in its beak and brings it to the nest. No Pie: a few coins pop. */
    private static void fetch(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token, ItemStack shown) {
        MagpieEntity magpie = magpieOf(world, space.getPos());
        if (magpie == null) {
            Vec3d at = above(token);
            world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, shown), at.x, at.y, at.z, 6, 0.15, 0.1, 0.15, 0.08);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.6F, 1.6F);
            return;
        }
        magpie.flyTo(() -> above(token), FLIGHT_TICKS, 1.5, false, () -> {
            Vec3d at = magpie.getPos();
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.7F, 1.5F);
        });
        // The stake in its beak on the way back
        magpie.flyHome(FLIGHT_TICKS, true, () -> {
            Vec3d at = magpie.getPos();
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.NEUTRAL, 0.5F, 1.8F);
            world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, shown), at.x, at.y, at.z, 4, 0.1, 0.05, 0.1, 0.03);
        });
    }

    /** The Pie brings the pot to the winner (coins bursting out there). No Pie: they burst from the space. */
    private static void deliver(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token, ItemStack coin) {
        MagpieEntity magpie = magpieOf(world, space.getPos());
        Runnable burst = () -> {
            Vec3d at = above(token);
            world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, coin), at.x, at.y, at.z, 16, 0.25, 0.2, 0.25, 0.12);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.7F, 1.2F);
        };
        if (magpie == null) {
            burst.run();
            return;
        }
        magpie.flyTo(() -> above(token), FLIGHT_TICKS + 4, 1.8, true, burst);
        magpie.flyHome(FLIGHT_TICKS, false, null);
    }
}
