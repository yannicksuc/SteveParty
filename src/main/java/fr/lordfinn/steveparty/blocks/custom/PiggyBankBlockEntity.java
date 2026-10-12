package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EventPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
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
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Piggy bank: on each redstone pulse, applies its inventory cartridge to its target (see {@link Target}): its linked
 * containers (none linked: the bank of the party around it, as a board space) give the cartridge items to each target
 * player, and take the items marked negative from them. With the "all items" selection it is all-or-nothing, so it can
 * sell a star for coins. The comparator gives 15 when the last pulse succeeded, 0 otherwise.
 * <p>
 * Its target and cartridge go to the clients (the panel over it when looked at); a payout bursts its items out of the
 * slot and over each player paid, with what each one got or paid floating there.
 */
public class PiggyBankBlockEntity extends CartridgeContainerBlockEntity {
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

    /** The menu's view of the target (its ordinal), set by its buttons. */
    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return target.ordinal();
        }

        @Override
        public void set(int index, int value) {
            setTarget(Target.byId(value));
        }

        @Override
        public int size() {
            return 1;
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
        for (ServerPlayerEntity player : findTargets(serverWorld)) {
            List<ItemStack> moved = new ArrayList<>();
            boolean paid = CartridgeTransfers.apply(serverWorld, getStack(0), this.pos, player, () -> cycleIndex,
                    index -> cycleIndex = index, (pattern, count) -> moved.add(pattern.copyWithCount(count)));
            if (paid) payoutEffects(serverWorld, player, moved);
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
     * Each kind moved bursts out of the slot (given) or into it (taken), and over the player, who sees what they got
     * (« +5 », green) or paid (« -20 », red) float above their head.
     */
    private void payoutEffects(ServerWorld world, ServerPlayerEntity player, List<ItemStack> moved) {
        Vec3d slot = slot();
        Vec3d head = player.getEyePos().add(0, 0.6, 0);
        boolean given = false;
        for (int i = 0; i < moved.size(); i++) {
            ItemStack stack = moved.get(i);
            int count = stack.getCount();
            ItemStackParticleEffect effect = new ItemStackParticleEffect(ParticleTypes.ITEM, stack.copyWithCount(1));
            world.spawnParticles(effect, slot.x, slot.y, slot.z, count > 0 ? 12 : 6, 0.08, 0.15, 0.08, count > 0 ? 0.12 : 0.04);
            world.spawnParticles(effect, head.x, head.y, head.z, count > 0 ? 10 : 6, 0.25, 0.15, 0.25, 0.08);
            Vector3f at = new Vector3f((float) head.x, (float) head.y + 0.35f * i, (float) head.z);
            FloatingTextParticleHelper.spawnFloatingText(world, (count > 0 ? "+" : "") + count, at,
                    count > 0 ? GIVE_COLOR : TAKE_COLOR, 32, 0.03f);
            given |= count > 0;
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                given ? SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP : SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.7f,
                given ? 1.2f : 0.7f);
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
        Optional<PartyControllerEntity> party = PartyControllerEntity.getClosestSteppablePartyControllerEntity(
                world, this.pos, PartyBellBlockEntity.RANGE, true);
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

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.piggy_bank");
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.writeNbt(nbt, wrapper);
        nbt.putString("Target", target.asString());
        nbt.putInt("CycleIndex", cycleIndex);
        nbt.putBoolean("LastSucceeded", lastSucceeded);
        nbt.putBoolean("InputPowered", inputPowered);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        target = Target.TURN_PLAYER;
        for (Target value : Target.values()) {
            if (value.asString().equals(nbt.getString("Target"))) target = value;
        }
        cycleIndex = nbt.getInt("CycleIndex");
        lastSucceeded = nbt.getBoolean("LastSucceeded");
        inputPowered = nbt.getBoolean("InputPowered");
    }
}
