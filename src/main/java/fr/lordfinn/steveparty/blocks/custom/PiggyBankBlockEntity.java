package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.board.PartyLink;
import fr.lordfinn.steveparty.board.PartyLinked;
import fr.lordfinn.steveparty.advancement.ModCriteria;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EventPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.screen_handlers.custom.PiggyBankScreenHandler;
import fr.lordfinn.steveparty.utils.FloatingTextParticleHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Piggy bank: on each redstone pulse, applies its inventory cartridge to its target (see {@link Target}): its linked
 * containers (none linked: the bank of its party) give the cartridge items to each target player, and take the items
 * marked negative from them. With the "all items" selection it is all-or-nothing, so it can sell a star for coins. The
 * comparator gives 15 when the last pulse succeeded, 0 otherwise.
 * <p>
 * Its party is the one of the element it is linked to with the Tile Linker Brush, only ({@link PartyLink}); unlinked,
 * a pulse does nothing (a sad grunt), and its panel and menu show it. Saved before it had to be linked, it links itself
 * once to the Party Controller nearest to it ({@link #MIGRATION_RANGE}), see {@link #serverTick}.
 * <p>
 * Its target and cartridge go to the clients (the panel over it when looked at); a payout bursts its items out of the
 * slot and over each player paid, with what each one got or paid floating there.
 */
public class PiggyBankBlockEntity extends CartridgeContainerBlockEntity implements ExtendedScreenHandlerFactory<BlockPosPayload>, PartyLinked {
    /** The party element it serves, painted with the Tile Linker Brush (see {@link PartyLink}); unlinked: none. */
    private final PartyLink partyLink = new PartyLink(this, this::syncLink);
    /**
     * A Piggy Bank saved before it had to be linked found its party alone, the nearest one this close: it links itself
     * once to the nearest Party Controller this close, then never again.
     */
    public static final int MIGRATION_RANGE = 64;
    /** How many times (a second apart) an old Piggy Bank looks for a controller to link to before it gives up. */
    private static final int MIGRATION_TRIES = 10;
    /** Whether it may still link itself (saved before it had to be linked, not done yet): see {@link #serverTick}. */
    private boolean migrating = false;
    private int migrationTries = 0;

    @Override
    public PartyLink partyLink() {
        return partyLink;
    }

    private void syncLink() {
        if (this.world == null || this.world.isClient) return;
        BlockState state = getCachedState();
        this.world.updateListeners(this.pos, state, state, Block.NOTIFY_LISTENERS);
    }

    /** The party it serves: the one of its link (running, or standing on its end); none unlinked. */
    private Optional<PartyControllerEntity> party(ServerWorld world) {
        return Optional.ofNullable(partyLink.runningParty(world, true));
    }

    /** Whether it is linked to a party element (the clients know it too: its panel and menu show it unlinked). */
    public boolean isLinked() {
        return partyLink.get() != null;
    }

    /**
     * Server tick: an old Piggy Bank (saved before it had to be linked) links itself to the Party Controller nearest to
     * it within {@link #MIGRATION_RANGE} blocks, a running party's first, as soon as one is loaded (a few tries a second
     * apart, then it stays unlinked). Done once: saved, it never links itself again.
     */
    public void serverTick(ServerWorld world) {
        if (!migrating || world.getTime() % 20 != Math.floorMod(this.pos.asLong(), 20)) return;
        if (partyLink.get() == null) {
            Optional<PartyControllerEntity> nearest = PartyControllerEntity.getClosestPartyControllerEntity(world, this.pos, MIGRATION_RANGE);
            if (nearest.isEmpty() && ++migrationTries < MIGRATION_TRIES) return;
            nearest.ifPresent(controller -> partyLink.set(GlobalPos.create(world.getRegistryKey(), controller.getPos().toImmutable())));
        }
        migrating = false;
        markDirty();
    }

    /** Radius in which the "nearest player" target (and the free play fallbacks) look for players. */
    public static final int NEAREST_PLAYER_RANGE = 8;
    /** A ringing party bell this close tells which turn a pulse is about (see {@link #turnToken}). */
    public static final int BELL_RANGE = 16;
    private static final int GIVE_COLOR = 0x55FF55, TAKE_COLOR = 0xFF5555;

    public enum Target implements StringIdentifiable {
        /**
         * The owner of the token whose turn it is; between two turns (a turn end bell, a mini-game, an event), the
         * one whose turn just ended. Free play: the nearest player.
         */
        TURN_PLAYER("turn_player"),
        /** The winners of the last mini-game of the party. */
        WINNERS("winners"),
        /** Every player of the party (free play: every player nearby). */
        EVERYONE("everyone"),
        /** The nearest player. */
        NEAREST("nearest");

        private final String name;

        Target(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }

        public Text getText() {
            return Text.translatable("piggy_bank_target.steveparty." + name);
        }

        /** What stands for it on its button and over the pig: the dice, the gold podium, the party, a compass. */
        public ItemStack getIcon() {
            return switch (this) {
                case TURN_PLAYER -> new ItemStack(ModItems.DEFAULT_DICE);
                case WINNERS -> new ItemStack(ModBlocks.GOLD_PODIUM);
                case EVERYONE -> new ItemStack(ModBlocks.PARTY_CONTROLLER);
                case NEAREST -> new ItemStack(Items.COMPASS);
            };
        }

        public static Target byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : TURN_PLAYER;
        }
    }

    private Target target = Target.TURN_PLAYER;
    private int cycleIndex = 0;
    private boolean lastSucceeded = false;
    private boolean inputPowered = false;

    /** The menu's view of the target (its ordinal), set by its buttons, and whether it is linked (1) or not (0). */
    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return index == 0 ? target.ordinal() : isLinked() ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) setTarget(Target.byId(value));
        }

        @Override
        public int size() {
            return 2;
        }
    };

    public PiggyBankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIGGY_BANK_ENTITY, pos, state, 1);
    }

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        if (this.target == target) return;
        this.target = target;
        markDirty();
    }

    public PropertyDelegate getProperties() {
        return properties;
    }

    public boolean hasLastSucceeded() {
        return lastSucceeded;
    }

    /** Every change goes to the clients too (the panel over the pig shows its target and cartridge). */
    @Override
    public void markDirty() {
        super.markDirty();
        if (this.world instanceof ServerWorld serverWorld && serverWorld.getServer().isOnThread())
            serverWorld.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }

    /** Neighbor update: acts on the rising edge of the received power. */
    public void onRedstoneInput(boolean powered) {
        boolean risingEdge = powered && !inputPowered;
        if (powered != inputPowered) {
            inputPowered = powered;
            markDirty();
        }
        if (risingEdge) activate();
    }

    public void initRedstoneInput(boolean powered) {
        this.inputPowered = powered;
    }

    /** Applies the cartridge to each target player. @return true if it succeeded for at least one of them */
    public boolean activate() {
        if (!(this.world instanceof ServerWorld serverWorld)) return false;
        boolean succeeded = false;
        // Unlinked: no party, nobody to pay (the sad grunt below)
        for (ServerPlayerEntity player : isLinked() ? findTargets(serverWorld) : List.<ServerPlayerEntity>of()) {
            List<Moved> moved = new ArrayList<>();
            boolean paid = CartridgeTransfers.apply(serverWorld, getStack(0), this.pos, player, () -> cycleIndex,
                    index -> cycleIndex = index, (pattern, count) -> moved.add(new Moved(pattern, count)));
            // The amounts float over the pig once (the player paid sees them there), and over each player paid
            if (paid) payoutEffects(serverWorld, player, moved, !succeeded);
            int given = moved.stream().mapToInt(m -> Math.max(0, m.count())).sum();
            if (paid && given > 0) ModCriteria.fire(player, "piggy_bank_payout", given);
            succeeded |= paid;
        }
        lastSucceeded = succeeded;
        markDirty();
        serverWorld.updateComparators(this.pos, getCachedState().getBlock());
        if (succeeded) {
            serverWorld.playSound(null, this.pos, SoundEvents.ENTITY_PIG_AMBIENT, SoundCategory.BLOCKS, 0.9f, 1.4f);
        } else {
            // A sad grunt and a puff from the snout: nobody to pay, nothing to give, or not enough
            serverWorld.playSound(null, this.pos, SoundEvents.ENTITY_PIG_AMBIENT, SoundCategory.BLOCKS, 0.8f, 0.6f);
            Vec3d snout = snout();
            serverWorld.spawnParticles(ParticleTypes.SMOKE, snout.x, snout.y, snout.z, 4, 0.05, 0.03, 0.05, 0.01);
        }
        return succeeded;
    }

    /**
     * Each kind moved bursts out of the slot (given) or into it (taken), and around the player; what they got
     * (« +5 », green) or paid (« -20 », red) floats over their head and, {@code overPig}, over the pig.
     */
    private void payoutEffects(ServerWorld world, ServerPlayerEntity player, List<Moved> moved, boolean overPig) {
        Vec3d slot = slot();
        Vec3d body = player.getPos().add(0, player.getHeight() * 0.6, 0);
        Vec3d head = player.getPos().add(0, player.getHeight() + 0.5, 0);
        boolean given = false;
        for (int i = 0; i < moved.size(); i++) {
            int count = moved.get(i).count();
            ItemStackParticleEffect effect = new ItemStackParticleEffect(ParticleTypes.ITEM, moved.get(i).pattern());
            world.spawnParticles(effect, slot.x, slot.y, slot.z, count > 0 ? 12 : 6, 0.08, 0.15, 0.08, count > 0 ? 0.12 : 0.04);
            world.spawnParticles(effect, body.x, body.y, body.z, count > 0 ? 10 : 6, 0.3, 0.3, 0.3, 0.06);
            String amount = (count > 0 ? "+" : "") + count;
            int color = count > 0 ? GIVE_COLOR : TAKE_COLOR;
            FloatingTextParticleHelper.spawnFloatingText(world, amount,
                    new Vector3f((float) head.x, (float) head.y + 0.35f * i, (float) head.z), color, 32, 0.03f);
            if (overPig) FloatingTextParticleHelper.spawnFloatingText(world, amount,
                    new Vector3f((float) slot.x, (float) slot.y + 0.4f + 0.35f * i, (float) slot.z), color, 32, 0.025f);
            given |= count > 0;
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                given ? SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP : SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.7f,
                given ? 1.2f : 0.7f);
    }

    /** {@code count} items like {@code pattern} given (positive) or taken (negative). */
    private record Moved(ItemStack pattern, int count) {
    }

    /** The coin slot on the pig's back. */
    private Vec3d slot() {
        Direction back = getCachedState().get(HorizontalFacingBlock.FACING).getOpposite();
        return new Vec3d(this.pos.getX() + 0.5 + back.getOffsetX() * 0.125, this.pos.getY() + 0.8,
                this.pos.getZ() + 0.5 + back.getOffsetZ() * 0.125);
    }

    private Vec3d snout() {
        Direction facing = getCachedState().get(HorizontalFacingBlock.FACING);
        return new Vec3d(this.pos.getX() + 0.5 + facing.getOffsetX() * 0.5, this.pos.getY() + 0.5,
                this.pos.getZ() + 0.5 + facing.getOffsetZ() * 0.5);
    }

    public List<ServerPlayerEntity> findTargets(ServerWorld world) {
        Optional<PartyControllerEntity> party = party(world);
        List<ServerPlayerEntity> players = new ArrayList<>();
        switch (target) {
            case TURN_PLAYER -> {
                if (party.isEmpty()) {
                    nearest(world).ifPresent(players::add);
                } else {
                    PartyMoment ringing = PartyBellBlockEntity.ringingNear(world, this.pos, party.get(), BELL_RANGE);
                    UUID token = turnToken(party.get().getPartyData(), ringing);
                    UUID owner = token == null ? null : party.get().getTokenOwner(token);
                    if (owner != null) addOnline(world, owner, players);
                }
            }
            case WINNERS -> party.ifPresent(controller -> controller.getLastWinners().forEach(uuid -> addOnline(world, uuid, players)));
            case EVERYONE -> {
                if (party.isPresent()) party.get().getPlayersInOrder().forEach(uuid -> addOnline(world, uuid, players));
                else players.addAll(world.getPlayers(player -> !player.isSpectator()
                        && player.squaredDistanceTo(this.pos.toCenterPos()) <= NEAREST_PLAYER_RANGE * NEAREST_PLAYER_RANGE));
            }
            case NEAREST -> nearest(world).ifPresent(players::add);
        }
        return players;
    }

    /**
     * The token whose turn a pulse is about: the turn being played; at a turn end (a bell ringing it near by, or the
     * party waiting on it) the turn just ended, at a turn start the one starting; out of the turns (mini-game, event,
     * end), the last turn played. Null before the first turn.
     *
     * @param ringing the moment of the party bell ringing near by, if any (see {@link PartyBellBlockEntity#ringingNear})
     */
    public static @Nullable UUID turnToken(PartyData data, @Nullable PartyMoment ringing) {
        List<PartyStep> steps = data.getSteps();
        int index = data.getStepIndex();
        PartyStep current = data.getCurrentStep();
        if (current instanceof EventPartyStep event && event.isTransition() && ringing == null) ringing = event.getWaitedMoment();
        if (ringing == PartyMoment.TURN_END) return turnBefore(steps, index);
        if (current instanceof TokenTurnPartyStep turn) return turn.getTokenUUID();
        if (current instanceof EventPartyStep event && event.isTransition()
                && (ringing == PartyMoment.TURN_START || ringing == PartyMoment.ROUND_START)
                && index + 1 < steps.size() && steps.get(index + 1) instanceof TokenTurnPartyStep next) {
            return next.getTokenUUID();
        }
        return turnBefore(steps, index);
    }

    private static @Nullable UUID turnBefore(List<PartyStep> steps, int index) {
        for (int i = Math.min(index, steps.size()) - 1; i >= 0; i--) {
            if (steps.get(i) instanceof TokenTurnPartyStep turn) return turn.getTokenUUID();
        }
        return null;
    }

    private Optional<ServerPlayerEntity> nearest(ServerWorld world) {
        Vec3d center = this.pos.toCenterPos();
        ServerPlayerEntity best = null;
        double bestDistance = (double) NEAREST_PLAYER_RANGE * NEAREST_PLAYER_RANGE;
        for (ServerPlayerEntity player : world.getPlayers()) {
            double distance = player.squaredDistanceTo(center);
            if (player.isSpectator() || distance > bestDistance) continue;
            best = player;
            bestDistance = distance;
        }
        return Optional.ofNullable(best);
    }

    private static void addOnline(ServerWorld world, UUID uuid, List<ServerPlayerEntity> players) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
        if (player != null && !players.contains(player)) players.add(player);
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new PiggyBankScreenHandler(syncId, playerInventory, this, properties);
    }

    /** The block's position: its cartridge's menu edits the cartridge here. */
    @Override
    public BlockPosPayload getScreenOpeningData(ServerPlayerEntity player) {
        return new BlockPosPayload(pos);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.piggy_bank");
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.writeNbt(nbt, wrapper);
        partyLink.write(nbt);
        if (migrating) nbt.putBoolean("LinkMigrating", true);
        else nbt.putBoolean("LinkSettled", true);
        nbt.putString("Target", target.asString());
        nbt.putInt("CycleIndex", cycleIndex);
        nbt.putBoolean("LastSucceeded", lastSucceeded);
        nbt.putBoolean("InputPowered", inputPowered);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        partyLink.read(nbt);
        // Saved before it had to be linked (neither key): links itself once (serverTick)
        migrating = nbt.getBoolean("LinkMigrating") || !nbt.contains("LinkSettled");
        target = Target.TURN_PLAYER;
        for (Target value : Target.values()) {
            if (value.asString().equals(nbt.getString("Target"))) target = value;
        }
        cycleIndex = nbt.getInt("CycleIndex");
        lastSucceeded = nbt.getBoolean("LastSucceeded");
        inputPowered = nbt.getBoolean("InputPowered");
    }
}
