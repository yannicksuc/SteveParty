package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlock;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.magpie.MagpieEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.utils.MessageUtils;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
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
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Common pots ({@link PotCartridgeItem}): a token passing over a pot space puts its stake in (all its player has
 * if less, no debt; up to the pot's cap), the token stopping exactly on it wins the pot (its coins, and the items the
 * thieving Pie stole) and the pot starts again. Coins only: the party's coin (the mod's coin outside a party). Its base
 * ({@link PotCartridgeItem#START}) is never made from nothing: it is taken from the party's bank ({@link PartyResources})
 * when a pot never filled is first used in a party and right after each win (a bank short of coins gives what it has;
 * outside a party, nothing). Several pots are independent: each cartridge holds its own (saved with its tile).
 * <p>
 * The Pie ({@link MagpieEntity}) lives on the nest ({@link MagpieNestBlock}) nearest to the space, within
 * {@link #NEST_RADIUS} blocks: it flies to fetch the stakes and brings the pot to its winner. The nest is linked to
 * the space ({@link MagpieNestBlockEntity#link}): its coins are the pot's (kept in the cartridge only), piled up in
 * it; coins put in the nest go into the pot. No nest: the pot works the same, without its keeper. Server thread only.
 */
public final class CommonPots {
    /** A nest this close to a pot space (any direction) is its nest. */
    public static final int NEST_RADIUS = 6;
    /** How often a pot space looks after its nest and its Pie. */
    public static final int CARE_INTERVAL = 40;
    private static final int FLIGHT_TICKS = 18;

    /** What a token passing gave the pot. */
    public record Stake(int coins, ItemStack stolen) {
        public static final Stake NONE = new Stake(0, ItemStack.EMPTY);
    }

    /** What a winner took: the coins and the items. */
    public record Win(int coins, List<ItemStack> items) {
        public static final Win NONE = new Win(0, List.of());

        public boolean isEmpty() {
            return coins == 0 && items.isEmpty();
        }
    }

    /** The nest of each pot space, as last found. */
    private static final Map<GlobalPos, BlockPos> NESTS = ServerMemory.forgetOnStop(new HashMap<>());

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

    private static @Nullable ItemStack starOf(MobEntity token) {
        return PartyControllerEntity.getRunningPartyOf(token.getUuid())
                .map(party -> party.getCurrency(PartyCurrency.STAR)).orElse(null);
    }

    /** The player of the token, if connected. */
    public static @Nullable ServerPlayerEntity playerOf(ServerWorld world, MobEntity token) {
        UUID owner = token instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenOwner() : null;
        return owner == null ? null : world.getServer().getPlayerManager().getPlayer(owner);
    }

    /**
     * {@code token} passes over the pot space: its player puts the stake in (what they have, no debt, as much as the
     * cap lets in), and the thieving Pie may steal one of their items.
     */
    public static Stake pass(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token) {
        ItemStack pot = potOf(space);
        ServerPlayerEntity player = playerOf(world, token);
        if (pot == null || player == null) return Stake.NONE;
        fillBase(pot, token);
        Stake stake = collect(world, space, pot, token, player, true);
        space.update();
        Text name = token.getDisplayName();
        Text message = stake.coins() > 0
                ? Text.translatable("message.steveparty.pot.stake", name, stake.coins(), PotCartridgeItem.coins(pot))
                : Text.translatable("message.steveparty.pot.no_stake", name, PotCartridgeItem.coins(pot));
        MessageUtils.sendToNearby(world, BoardSpaces.standPos(world, space.getPos()), 100, message.copy().formatted(Formatting.GOLD),
                MessageUtils.MessageType.ACTION_BAR);
        if (!stake.stolen().isEmpty()) {
            MessageUtils.sendToNearby(world, BoardSpaces.standPos(world, space.getPos()), 100,
                    Text.translatable("message.steveparty.pot.stolen", player.getDisplayName(), stake.stolen().toHoverableText())
                            .formatted(Formatting.DARK_AQUA), MessageUtils.MessageType.CHAT);
        }
        if (stake.coins() > 0 || !stake.stolen().isEmpty()) fetch(world, space, token, stake.stolen().isEmpty() ? coinOf(token) : stake.stolen());
        return stake;
    }

    /**
     * Its base still due (a pot never filled, or won since) and {@code token} in a running party: the pot is filled up
     * to its base from that party's bank, as far as the bank and the cap allow, and is no longer due. Outside a party,
     * nothing (it stays due). @return the coins taken from the bank
     */
    public static int fillBase(ItemStack pot, MobEntity token) {
        if (!PotCartridgeItem.baseDue(pot)) return 0;
        return fillBase(pot, PartyControllerEntity.getRunningPartyOf(token.getUuid()).orElse(null));
    }

    /** The same from the bank of {@code party} (null: nothing). */
    public static int fillBase(ItemStack pot, @Nullable PartyControllerEntity party) {
        if (!PotCartridgeItem.baseDue(pot) || party == null) return 0;
        int coins = PotCartridgeItem.coins(pot), cap = PotCartridgeItem.cap(pot);
        int wanted = Math.max(0, PotCartridgeItem.start(pot) - coins);
        if (cap > 0) wanted = Math.min(wanted, Math.max(0, cap - coins));
        int taken = PartyResources.of(party).take(party.getCurrency(PartyCurrency.COIN), wanted);
        PotCartridgeItem.setCoins(pot, coins + taken);
        PotCartridgeItem.setBaseDue(pot, false);
        return taken;
    }

    /** Takes the stake (and maybe an item) from {@code player} into {@code pot}. */
    private static Stake collect(ServerWorld world, BoardSpaceBlockEntity space, ItemStack pot, MobEntity token,
                                 ServerPlayerEntity player, boolean thief) {
        int coins = PotCartridgeItem.coins(pot);
        int cap = PotCartridgeItem.cap(pot);
        int room = cap > 0 ? Math.max(0, cap - coins) : Integer.MAX_VALUE;
        int taken = InventoryUtils.take(player.getInventory(), coinOf(token), Math.min(PotCartridgeItem.stake(pot), room));
        PotCartridgeItem.setCoins(pot, coins + taken);
        ItemStack stolen = ItemStack.EMPTY;
        int chance = PotCartridgeItem.thief(pot);
        if (thief && chance > 0 && world.random.nextInt(100) < chance) {
            stolen = steal(world, player, token);
            if (!stolen.isEmpty()) {
                List<ItemStack> items = PotCartridgeItem.items(pot);
                merge(items, stolen);
                PotCartridgeItem.setItems(pot, items);
            }
        }
        updateNest(world, space, pot);
        return new Stake(taken, stolen);
    }

    /** One item (not the party's coins or stars) taken from the player's inventory, empty if none. */
    private static ItemStack steal(ServerWorld world, ServerPlayerEntity player, MobEntity token) {
        ItemStack coin = coinOf(token), star = starOf(token);
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().main.size(); slot++) {
            ItemStack stack = player.getInventory().main.get(slot);
            if (stack.isEmpty() || ItemStack.areItemsAndComponentsEqual(stack, coin)
                    || star != null && ItemStack.areItemsAndComponentsEqual(stack, star)) continue;
            slots.add(slot);
        }
        if (slots.isEmpty()) return ItemStack.EMPTY;
        int slot = slots.get(world.random.nextInt(slots.size()));
        ItemStack taken = player.getInventory().main.get(slot).split(1);
        player.getInventory().markDirty();
        return taken;
    }

    private static void merge(List<ItemStack> items, ItemStack added) {
        for (ItemStack item : items) {
            if (ItemStack.areItemsAndComponentsEqual(item, added) && item.getCount() < item.getMaxCount()) {
                item.increment(added.getCount());
                return;
            }
        }
        items.add(added.copy());
    }

    /**
     * {@code token} stopped exactly on the pot space: its player wins the pot (paying a stake first if the cartridge
     * says so); the pot starts again from its start.
     */
    public static Win win(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token) {
        ItemStack pot = potOf(space);
        ServerPlayerEntity player = playerOf(world, token);
        if (pot == null || player == null) return Win.NONE;
        fillBase(pot, token);
        if (PotCartridgeItem.landerPays(pot)) collect(world, space, pot, token, player, false);
        int coins = PotCartridgeItem.coins(pot);
        List<ItemStack> items = PotCartridgeItem.items(pot);
        Win win = new Win(coins, items);
        if (coins > 0) InventoryUtils.giveOrDrop(player, coinOf(token), coins);
        for (ItemStack item : items) player.getInventory().offerOrDrop(item.copy());
        // It starts again: its base from the party's bank, nothing made
        PotCartridgeItem.setCoins(pot, 0);
        PotCartridgeItem.setItems(pot, List.of());
        PotCartridgeItem.setBaseDue(pot, true);
        fillBase(pot, token);
        updateNest(world, space, pot);
        space.update();
        Vec3d at = BoardSpaces.standPos(world, space.getPos());
        if (win.isEmpty()) {
            MessageUtils.sendToNearby(world, at, 100, Text.translatable("message.steveparty.pot.empty", token.getDisplayName())
                    .formatted(Formatting.GRAY), MessageUtils.MessageType.ACTION_BAR);
            return win;
        }
        int stolen = items.stream().mapToInt(ItemStack::getCount).sum();
        Text message = stolen > 0
                ? Text.translatable("message.steveparty.pot.won_items", player.getDisplayName(), coins, stolen)
                : Text.translatable("message.steveparty.pot.won", player.getDisplayName(), coins);
        MessageUtils.sendToNearby(world, at, 100, message.copy().formatted(Formatting.GOLD, Formatting.BOLD), MessageUtils.MessageType.CHAT);
        fanfare(world, at);
        deliver(world, space, token, coinOf(token));
        return win;
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

    /** The nest of the pot space at {@code space} (the nearest one within {@link #NEST_RADIUS} not kept by another pot's Pie), or null. */
    public static @Nullable BlockPos nestOf(ServerWorld world, BlockPos space) {
        GlobalPos key = GlobalPos.create(world.getRegistryKey(), space.toImmutable());
        BlockPos known = NESTS.get(key);
        if (known != null && world.isChunkLoaded(known) && world.getBlockState(known).getBlock() instanceof MagpieNestBlock) return known;
        NESTS.remove(key);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(space.add(-NEST_RADIUS, -NEST_RADIUS, -NEST_RADIUS), space.add(NEST_RADIUS, NEST_RADIUS, NEST_RADIUS))) {
            if (!(world.getBlockState(pos).getBlock() instanceof MagpieNestBlock)) continue;
            if (isKeptByAnother(world, pos, space)) continue;
            double distance = pos.getSquaredDistance(space);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.toImmutable();
            }
        }
        if (best != null) NESTS.put(key, best);
        return best;
    }

    private static boolean isKeptByAnother(ServerWorld world, BlockPos nest, BlockPos space) {
        MagpieNestBlockEntity linked = MagpieNestBlockEntity.at(world, nest);
        if (linked != null && linked.getPot() != null && !linked.getPot().equals(space) && linked.isLinked()) return true;
        for (MagpieEntity magpie : magpiesAround(world, nest)) {
            if (nest.equals(magpie.getNest()) && magpie.getHome() != null && !magpie.getHome().equals(space)) return true;
        }
        return false;
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
     * Every {@link #CARE_INTERVAL} ticks, by the pot space: a party running on its board fills its base due from its
     * bank, the nest is linked to it (its coins are the pot), a nest without its Pie gets one.
     * The Pie leaves by itself when the cartridge or the nest goes (MagpieEntity).
     */
    public static void care(ServerWorld world, BoardSpaceBlockEntity space, ItemStack pot) {
        // A party running on its board: its base, still due, comes from that party's bank at once
        if (PotCartridgeItem.baseDue(pot)) {
            PartyControllerEntity party = PartyControllerEntity.getClosestSteppablePartyControllerEntity(world, space.getPos(),
                    PartyControllerEntity.START_TILES_SEARCH_RADIUS, false).orElse(null);
            if (party != null) {
                fillBase(pot, party);
                updateNest(world, space, pot);
                space.update();
            }
        }
        BlockPos nest = nestOf(world, space.getPos());
        if (nest == null) return;
        updateNest(world, space, pot);
        if (magpieOf(world, space.getPos()) != null) return;
        MagpieEntity magpie = MagpieEntity.create(ModEntities.MAGPIE, world, space.getPos(), nest);
        // A hologram like every mob a space summons: never saved, the space gives it back after a reload
        BoardActors.mark(magpie);
        world.spawnEntity(magpie);
        world.spawnParticles(ParticleTypes.CLOUD, magpie.getX(), magpie.getY() + 0.3, magpie.getZ(), 5, 0.2, 0.2, 0.2, 0.01);
    }

    /** The nest of the space linked to it, showing the pot's coins. */
    private static void updateNest(ServerWorld world, BoardSpaceBlockEntity space, ItemStack pot) {
        BlockPos nest = nestOf(world, space.getPos());
        MagpieNestBlockEntity entity = nest == null ? null : MagpieNestBlockEntity.at(world, nest);
        if (entity != null) entity.link(space.getPos());
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
