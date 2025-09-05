package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainer;
import fr.lordfinn.steveparty.screen_handlers.custom.HopSwitchScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.TileScreenHandler;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.sound.SoundEvents.BLOCK_STONE_BUTTON_CLICK_OFF;
import static net.minecraft.sound.SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON;
import static net.minecraft.util.ActionResult.SUCCESS;

public class HopSwitchBlock extends CartridgeContainer {

    // -----------------------------
    // Static constants and properties
    // -----------------------------
    public static final MapCodec<HopSwitchBlock> CODEC = Block.createCodec(HopSwitchBlock::new);
    public static final BooleanProperty PRESSED = BooleanProperty.of("pressed");

    private static final int DEFAULT_DURATION = 200;

    // Shapes
    private static final VoxelShape BASE = Block.createCuboidShape(0, 0, 0, 16, 2, 16);
    private static final VoxelShape TOP_UP = Block.createCuboidShape(3, 2, 3, 13, 12, 13);
    private static final VoxelShape TOP_DOWN = Block.createCuboidShape(3, 2, 3, 13, 4, 13);

    private static final VoxelShape SHAPE_UNPRESSED = VoxelShapes.union(BASE, TOP_UP);
    private static final VoxelShape SHAPE_PRESSED = VoxelShapes.union(BASE, TOP_DOWN);

    // Durations in seconds
    private static final double[] DURATIONS_SECONDS = new double[]{
            0.5, 1, 1.5, 2, 2.5, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            20, 25, 30, 40, 50, 60, 70, 80, 90, 105, 120, 150
    };

    // -----------------------------
    // Constructor
    // -----------------------------
    public HopSwitchBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(PRESSED, false));
    }

    // -----------------------------
    // Event registration
    // -----------------------------
    public static void registerUseBlockCallback() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;

            return handleClockSneakInteraction(player, world, hand, hitResult);
        });
    }

    private static ActionResult handleClockSneakInteraction(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(Items.CLOCK) || !player.isSneaking()) return ActionResult.PASS;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof HopSwitchBlock)) return ActionResult.PASS;

        HopSwitchBlockEntity be = (HopSwitchBlockEntity) world.getBlockEntity(pos);
        if (be != null) decreaseDuration(be, player);

        return ActionResult.SUCCESS;
    }

    private void increaseDuration(HopSwitchBlockEntity be, PlayerEntity player) {
        int newDuration = getNextDuration(be.getDurationTicks());
        be.setDurationTicks(newDuration);
        sendDurationMessage(be, player, true);
    }

    private static void decreaseDuration(HopSwitchBlockEntity be, PlayerEntity player) {
        int newDuration = getPreviousDuration(be.getDurationTicks());
        be.setDurationTicks(newDuration);
        sendDurationMessage(be, player, false);
    }

    private static void sendDurationMessage(HopSwitchBlockEntity be, PlayerEntity player, boolean added) {
        int totalTicks = be.getDurationTicks();
        double totalSeconds = totalTicks / 20.0;

        int minutes = (int) (totalSeconds / 60);
        double seconds = totalSeconds % 60;

        // Format seconds with 1 decimal if needed
        String secondsStr = (seconds % 1 == 0)
                ? String.format("%d", (int) seconds)
                : String.format("%.1f", seconds).replace('.', ',');

        String formattedDuration = (minutes > 0)
                ? String.format("%dm%ss", minutes, secondsStr)
                : String.format("%ss", secondsStr);

        String key = added
                ? "message.steveparty.hopswitch.time.added"
                : "message.steveparty.hopswitch.time.removed";

        player.sendMessage(Text.literal(formattedDuration), true); // optionally wrap in translatable key if needed
    }


    // -----------------------------
    // Block properties
    // -----------------------------
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(PRESSED);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new HopSwitchBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<HopSwitchBlock> getCodec() {
        return CODEC;
    }

    // -----------------------------
    // Interaction (right-click)
    // -----------------------------
    @Override
    protected ActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state,
                                                                World world, BlockPos pos,
                                                                PlayerEntity player, Hand hand,
                                                                BlockHitResult hit) {
        if (world.isClient) return ActionResult.PASS;

        if (!stack.isOf(Items.CLOCK)) return ActionResult.PASS;

        HopSwitchBlockEntity be = (HopSwitchBlockEntity) world.getBlockEntity(pos);
        if (be != null) increaseDuration(be, player);

        return ActionResult.CONSUME;
    }
    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, net.minecraft.world.World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return new SimpleNamedScreenHandlerFactory((syncId, inventory, player) ->
                new HopSwitchScreenHandler(syncId, inventory, (HopSwitchBlockEntity) blockEntity), Text.empty());
    }

    @Override
    protected ActionResult.@Nullable Success openScreen(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (world.isClient) return null;

        HopSwitchBlockEntity blockEntity = (HopSwitchBlockEntity) world.getBlockEntity(pos);
        world.playSound(null, pos, ModSounds.OPEN_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);

        if (player instanceof ServerPlayerEntity serverPlayer && blockEntity != null) {
            serverPlayer.openHandledScreen(blockEntity);
        }

        return SUCCESS;
    }

    // -----------------------------
    // Falling interaction
    // -----------------------------
    @Override
    public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        super.onLandedUpon(world, state, pos, entity, fallDistance);

        if (!world.isClient && !state.get(PRESSED)) {
            activate(state, world, pos);
        }
    }

    // -----------------------------
    // Utility methods
    // -----------------------------
    private void deactivate(BlockState state, ServerWorld world, BlockPos pos) {
        world.setBlockState(pos, state.with(PRESSED, false), Block.NOTIFY_ALL);
        world.playSound(null, pos, getDefaultState().getSoundGroup().getHitSound(),
                SoundCategory.BLOCKS, 1.0f, 1.0f);
        HopSwitchBlockEntity be = (HopSwitchBlockEntity) world.getBlockEntity(pos);

        playTriggerSound(pos, world, BLOCK_STONE_BUTTON_CLICK_OFF);

        triggerTargets(be, true);

        // Launch entities above the block
        Box box = new Box(pos).expand(0, 0.5, 0); // slightly bigger than block
        for (Entity entity : world.getEntitiesByClass(Entity.class, box, e -> true)) {
            // Only launch entities standing on the block
            if (entity.getY() <= pos.getY() + 1.0) {
                entity.setVelocity(entity.getVelocity().add(0, 0.5 + 0.2 * entity.getRandom().nextDouble(), 0));
                entity.velocityModified = true; // ensure velocity is applied
            }
        }
    }

    private static void triggerTargets(HopSwitchBlockEntity be, boolean invert) {
        if (be == null) return;
        switch (be.getMode()) {
            case FORCE_APPEAR -> be.solidifyDestinations(!invert);
            case FORCE_DISAPPEAR -> be.solidifyDestinations(invert);
            default -> be.switchDestinations();
        }
    }

    private void activate(BlockState state, World world, BlockPos pos) {
        HopSwitchBlockEntity be = (HopSwitchBlockEntity) world.getBlockEntity(pos);
        int duration = (be != null) ? be.getDurationTicks() : DEFAULT_DURATION;
        triggerTargets(be, false);

        world.setBlockState(pos, state.with(PRESSED, true), Block.NOTIFY_ALL);
        world.scheduleBlockTick(pos, state.getBlock(), duration);

        playTriggerSound(pos, world, BLOCK_STONE_BUTTON_CLICK_ON);

        ((ServerWorld) world).spawnParticles(ParticleTypes.GLOW,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                5, 0.1, 0.1, 0.1, 0);
    }

    private static void playTriggerSound(BlockPos pos, World world, SoundEvent blockStoneButtonClickOn) {
        BlockPos belowPos = pos.down();
        if (!world.getBlockState(belowPos).isIn(BlockTags.WOOL)) {
            world.playSound(null, pos, blockStoneButtonClickOn,
                    SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
    }

    // -----------------------------
    // Scheduled tick (unpress)
    // -----------------------------
    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.get(PRESSED)) {
            deactivate(state, world, pos);
        }
    }

    @Override
    public boolean hasRandomTicks(BlockState state) {
        return true;
    }

    // -----------------------------
    // Redstone neighbor update
    // -----------------------------
    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
                                  @Nullable WireOrientation wireOrientation, boolean notify) {
        if (world.isClient) return;

        boolean powered = world.isReceivingRedstonePower(pos);
        if (powered && !state.get(PRESSED)) {
            activate(state, world, pos);
        }
    }

    // -----------------------------
    // Duration helpers
    // -----------------------------
    protected int getNextDuration(int currentTicks) {
        double currentSec = currentTicks / 20.0;
        for (double sec : DURATIONS_SECONDS) {
            if (sec > currentSec) return (int) (sec * 20);
        }
        return currentTicks + 20 * 30; // add 30 seconds if above last value
    }

    protected static int getPreviousDuration(int currentTicks) {
        double currentSec = currentTicks / 20.0;

        if (currentSec > DURATIONS_SECONDS[DURATIONS_SECONDS.length - 1]) {
            return currentTicks - 20 * 30;
        }

        for (int i = DURATIONS_SECONDS.length - 1; i >= 0; i--) {
            if (DURATIONS_SECONDS[i] < currentSec) return (int) (DURATIONS_SECONDS[i] * 20);
        }
        return currentTicks;
    }

    // -----------------------------
    // Redstone output
    // -----------------------------
    @Override
    public boolean emitsRedstonePower(BlockState state) { return false; }

    @Override
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) { return 0; }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) { return 0; }
    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }
    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return state.get(PRESSED) ? 15 : 0;
    }

    // -----------------------------
    // Shape
    // -----------------------------
    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(PRESSED) ? SHAPE_PRESSED : SHAPE_UNPRESSED;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(PRESSED) ? SHAPE_PRESSED : SHAPE_UNPRESSED;
    }
}
