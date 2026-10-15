package fr.lordfinn.steveparty.board.copy;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.HopSwitchBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlock;
import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyController;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.StepPulse;
import fr.lordfinn.steveparty.blocks.custom.PiggyBankBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlock;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainer;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.podium.PodiumSignal;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.service.MarkerResidents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Every kind of block whose settings the Wrench copies, and what it copies of each (the wiki's table says the same):
 * <ul>
 *     <li>the holders of cartridges (tiles, Checkpoints, routers, Hop Switches, Piggy Banks, Looting Boxes): the settings
 *     of their cartridges ({@link CartridgeSettings}), on the cartridges of the same kind; on the same block, its own
 *     settings too (a Hop Switch's mode and duration, a Piggy Bank's target);</li>
 *     <li>a podium: its mode; a goal pole base: what it counts and whose points; the Party Controller: its rules
 *     (Settings and Gains pages, rounds); a Spawn Bubble: when it shows its mob, its height, which way it faces; a
 *     Magnéto: its mode; a Party Antenna: its moment (never « waiting »); a road sign: its plate;</li>
 *     <li>a Mini-game Controller: its page, in creative only (a page is an item).</li>
 * </ul>
 * Not copied: a pipe (what it is fixed to depends on its neighbours).
 */
public final class BlockSettingsKinds {
    private BlockSettingsKinds() {
    }

    public static final List<BlockSettings> ALL = List.of(
            new Cartridges(), new Podiums(), new GoalPoleBases(), new PartyControllers(), new MiniGameControllers(),
            new SpawnBubbles(), new Magnetos(),
            new StateSettings("party_antenna", state -> state.getBlock() instanceof PartyBellBlock, PartyBellBlock.MOMENT) {
                @Override
                protected BlockState pasted(BlockState state) {
                    // « Waiting » is never copied: a moment that can't wait doesn't keep it either
                    return state.get(PartyBellBlock.WAITING) && !state.get(PartyBellBlock.MOMENT).canWait()
                            ? state.with(PartyBellBlock.WAITING, false) : state;
                }
            },
            new StateSettings("road_sign", state -> state.getBlock() instanceof PlasticRoadSignBlock, PlasticRoadSignBlock.PLATE));

    /** The kind of {@code state}, null if the Wrench copies nothing of it. */
    public static @Nullable BlockSettings of(BlockState state) {
        for (BlockSettings kind : ALL) if (kind.handles(state)) return kind;
        return null;
    }

    private static boolean sameBlock(BlockSettings.Paste paste) {
        return Registries.BLOCK.getId(paste.world().getBlockState(paste.pos()).getBlock()).equals(paste.source());
    }

    // ---------------------------------------------------------------- holders of cartridges

    private static final class Cartridges implements BlockSettings {
        @Override
        public String family() {
            return "cartridges";
        }

        @Override
        public boolean handles(BlockState state) {
            return state.getBlock() instanceof CartridgeContainer;
        }

        @Override
        public BlockPos settingsPos(World world, BlockPos pos) {
            return BoardSpaces.resolve(world, pos);
        }

        @Override
        public @Nullable NbtCompound write(ServerWorld world, BlockPos pos, RegistryWrapper.WrapperLookup registries, boolean creative) {
            if (!(world.getBlockEntity(pos) instanceof CartridgeContainerBlockEntity container)) return null;
            NbtCompound settings = new NbtCompound();
            NbtList slots = new NbtList();
            for (int slot = 0; slot < container.size(); slot++) {
                ItemStack cartridge = container.getStack(slot);
                if (cartridge.isEmpty()) continue;
                NbtCompound entry = new NbtCompound();
                entry.putInt("Slot", slot);
                entry.put("Cartridge", CartridgeSettings.settingsOnly(cartridge).encode(registries));
                slots.add(entry);
            }
            if (!slots.isEmpty()) settings.put("Slots", slots);
            if (container instanceof HopSwitchBlockEntity hopSwitch) {
                settings.putString("Mode", hopSwitch.getMode().name());
                settings.putInt("DurationTicks", hopSwitch.getDurationTicks());
            }
            if (container instanceof PiggyBankBlockEntity piggyBank) settings.putString("Target", piggyBank.getTarget().asString());
            return settings.isEmpty() ? null : settings;
        }

        @Override
        public Pasted paste(Paste paste) {
            if (!(paste.world().getBlockEntity(paste.pos()) instanceof CartridgeContainerBlockEntity container)) return Pasted.of(false);
            boolean same = sameBlock(paste);
            boolean changed = false;
            boolean[] taken = new boolean[container.size()];
            List<Text> missing = new ArrayList<>();
            for (NbtElement element : paste.settings().getList("Slots", NbtElement.COMPOUND_TYPE)) {
                NbtCompound entry = (NbtCompound) element;
                ItemStack source = ItemStack.fromNbt(paste.registries(), entry.get("Cartridge")).orElse(ItemStack.EMPTY);
                if (source.isEmpty()) continue;
                int sourceSlot = same ? entry.getInt("Slot") : -1;
                int slot = slotFor(container, taken, sourceSlot, stack -> stack.isOf(source.getItem()));
                if (slot >= 0) {
                    taken[slot] = true;
                    changed |= CartridgeSettings.apply(source, container.getStack(slot));
                    continue;
                }
                // Creative: a copy fills the same slot when empty, else the first empty one; survival: never created
                slot = paste.creative() ? slotFor(container, taken, sourceSlot, ItemStack::isEmpty) : -1;
                if (slot >= 0) {
                    taken[slot] = true;
                    container.setStack(slot, CartridgeSettings.settingsOnly(source));
                    changed = true;
                } else if (missing.stream().noneMatch(name -> name.getString().equals(source.getName().getString()))) {
                    missing.add(source.getName());
                }
            }
            if (same) changed |= pasteOwn(container, paste.settings());
            if (changed) BoardLinks.sync(container);
            return new Pasted(changed, missing, null);
        }

        /** The slot {@code wanted} of the source if it fits, else the first one that does (not taken yet); -1 if none. */
        private static int slotFor(CartridgeContainerBlockEntity container, boolean[] taken, int wanted, Predicate<ItemStack> fits) {
            if (wanted >= 0 && wanted < container.size() && !taken[wanted] && fits.test(container.getStack(wanted))) return wanted;
            for (int slot = 0; slot < container.size(); slot++) if (!taken[slot] && fits.test(container.getStack(slot))) return slot;
            return -1;
        }

        /** The block's own settings (same block only). */
        private static boolean pasteOwn(CartridgeContainerBlockEntity container, NbtCompound settings) {
            boolean changed = false;
            if (container instanceof HopSwitchBlockEntity hopSwitch && settings.contains("Mode")) {
                HopSwitchBlockEntity.Mode mode = GoalPoleBaseBlockEntity.readEnum(settings, "Mode", HopSwitchBlockEntity.Mode.values(), hopSwitch.getMode());
                int duration = settings.getInt("DurationTicks");
                changed = mode != hopSwitch.getMode() || duration != hopSwitch.getDurationTicks();
                hopSwitch.setMode(mode);
                hopSwitch.setDurationTicks(duration);
            }
            if (container instanceof PiggyBankBlockEntity piggyBank && settings.contains("Target")) {
                for (PiggyBankBlockEntity.Target target : PiggyBankBlockEntity.Target.values()) {
                    if (!target.asString().equals(settings.getString("Target")) || target == piggyBank.getTarget()) continue;
                    piggyBank.setTarget(target);
                    changed = true;
                }
            }
            return changed;
        }
    }

    // ---------------------------------------------------------------- podiums

    private static final class Podiums implements BlockSettings {
        @Override
        public String family() {
            return "podium";
        }

        @Override
        public boolean handles(BlockState state) {
            return PodiumBlock.isPodium(state);
        }

        @Override
        public BlockPos settingsPos(World world, BlockPos pos) {
            PodiumBlockEntity master = PodiumBlock.master(world, pos);
            return master == null ? pos : master.getPos();
        }

        @Override
        public boolean mayEdit(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
            return MiniGamePages.canEdit(player) && ScreenHandlerChecks.canBuildAt(player, pos);
        }

        @Override
        public @Nullable NbtCompound write(ServerWorld world, BlockPos pos, RegistryWrapper.WrapperLookup registries, boolean creative) {
            if (!(world.getBlockEntity(pos) instanceof PodiumBlockEntity podium)) return null;
            NbtCompound settings = new NbtCompound();
            settings.putString("Signal", podium.getSignal().name());
            return settings;
        }

        @Override
        public Pasted paste(Paste paste) {
            if (!(paste.world().getBlockEntity(paste.pos()) instanceof PodiumBlockEntity podium)) return Pasted.of(false);
            PodiumSignal signal = PodiumSignal.byName(paste.settings().getString("Signal"));
            // A pulse-only mode (empty the column, reset the group) on a podium no pulse takes
            if (!signal.allowed(PodiumBlock.answersRedstone(paste.world(), paste.pos())))
                return Pasted.refused(Text.translatable("message.steveparty.wrench.copy.goes_on", ModBlocks.PODIUM.getName()));
            if (signal == podium.getSignal()) return Pasted.of(false);
            podium.setSignal(signal);
            return Pasted.of(true);
        }
    }

    // ---------------------------------------------------------------- goal pole bases

    private static final class GoalPoleBases implements BlockSettings {
        private static final List<String> KEYS = List.of("Source", "Criterion", "Selector", "Players", "Radius");

        @Override
        public String family() {
            return "goal_pole_base";
        }

        @Override
        public boolean handles(BlockState state) {
            return state.getBlock() instanceof GoalPoleBaseBlock;
        }

        @Override
        public @Nullable NbtCompound write(ServerWorld world, BlockPos pos, RegistryWrapper.WrapperLookup registries, boolean creative) {
            return world.getBlockEntity(pos) instanceof GoalPoleBaseBlockEntity base ? settingsOf(base) : null;
        }

        private static NbtCompound settingsOf(GoalPoleBaseBlockEntity base) {
            NbtCompound all = base.writeSettings(), settings = new NbtCompound();
            for (String key : KEYS) if (all.contains(key)) settings.put(key, all.get(key));
            return settings;
        }

        @Override
        public Pasted paste(Paste paste) {
            if (!(paste.world().getBlockEntity(paste.pos()) instanceof GoalPoleBaseBlockEntity base)) return Pasted.of(false);
            NbtCompound before = settingsOf(base);
            NbtCompound settings = paste.settings().copy();
            settings.remove("Reset"); // its points are the party's
            base.applySettings(settings);
            return Pasted.of(!before.equals(settingsOf(base)));
        }
    }

    // ---------------------------------------------------------------- Party Controller

    private static final class PartyControllers implements BlockSettings {
        @Override
        public String family() {
            return "party_controller";
        }

        @Override
        public boolean handles(BlockState state) {
            return state.getBlock() instanceof PartyController;
        }

        @Override
        public boolean mayEdit(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
            return world.getBlockEntity(pos) instanceof PartyControllerEntity controller && controller.canEdit(player);
        }

        @Override
        public @Nullable NbtCompound write(ServerWorld world, BlockPos pos, RegistryWrapper.WrapperLookup registries, boolean creative) {
            return world.getBlockEntity(pos) instanceof PartyControllerEntity controller ? controller.writeRules(registries) : null;
        }

        @Override
        public Pasted paste(Paste paste) {
            if (!(paste.world().getBlockEntity(paste.pos()) instanceof PartyControllerEntity controller)) return Pasted.of(false);
            return Pasted.of(controller.readRules(paste.settings(), paste.registries(), paste.player()));
        }
    }

    // ---------------------------------------------------------------- Mini-game Controller

    private static final class MiniGameControllers implements BlockSettings {
        @Override
        public String family() {
            return "mini_game_controller";
        }

        @Override
        public boolean handles(BlockState state) {
            return state.getBlock() instanceof MiniGameControllerBlock;
        }

        @Override
        public boolean mayEdit(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
            return MiniGamePages.canEdit(player) && world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity controller
                    && !controller.isLockedFor(player);
        }

        /** Its page, the only thing it holds: an item, copied in creative only. */
        @Override
        public @Nullable NbtCompound write(ServerWorld world, BlockPos pos, RegistryWrapper.WrapperLookup registries, boolean creative) {
            if (!creative || !(world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity controller) || controller.getPage().isEmpty()) return null;
            NbtCompound settings = new NbtCompound();
            settings.put("Page", controller.getPage().encode(registries));
            return settings;
        }

        @Override
        public Pasted paste(Paste paste) {
            if (!(paste.world().getBlockEntity(paste.pos()) instanceof MiniGameControllerBlockEntity controller)) return Pasted.of(false);
            if (!paste.creative()) return Pasted.refused(Text.translatable("message.steveparty.wrench.copy.items_only"));
            ItemStack page = ItemStack.fromNbt(paste.registries(), paste.settings().get("Page")).orElse(ItemStack.EMPTY);
            if (page.isEmpty() || !controller.getPage().isEmpty()) return Pasted.of(false); // its own page stays
            controller.setPage(page.copyWithCount(1));
            return Pasted.of(true);
        }
    }

    // ---------------------------------------------------------------- Spawn Bubble

    private static final class SpawnBubbles implements BlockSettings {
        @Override
        public String family() {
            return "spawn_bubble";
        }

        @Override
        public boolean handles(BlockState state) {
            return state.getBlock() instanceof SpawnMarkerBlock;
        }

        @Override
        public @Nullable NbtCompound write(ServerWorld world, BlockPos pos, RegistryWrapper.WrapperLookup registries, boolean creative) {
            if (!(world.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity marker)) return null;
            NbtCompound settings = new NbtCompound();
            settings.putBoolean("Resident", marker.isResident());
            settings.putInt("Lift", marker.getLiftSteps());
            settings.putInt("Rotation", world.getBlockState(pos).get(SpawnMarkerBlock.ROTATION));
            return settings;
        }

        @Override
        public Pasted paste(Paste paste) {
            ServerWorld world = paste.world();
            if (!(world.getBlockEntity(paste.pos()) instanceof SpawnMarkerBlockEntity marker)) return Pasted.of(false);
            NbtCompound settings = paste.settings();
            BlockState state = world.getBlockState(paste.pos());
            int rotation = settings.getInt("Rotation") & 15;
            boolean changed = marker.isResident() != settings.getBoolean("Resident") || marker.getLiftSteps() != settings.getInt("Lift")
                    || state.get(SpawnMarkerBlock.ROTATION) != rotation;
            if (!changed) return Pasted.of(false);
            marker.setResident(settings.getBoolean("Resident"));
            marker.setLiftSteps(settings.getInt("Lift"));
            world.setBlockState(paste.pos(), state.with(SpawnMarkerBlock.ROTATION, rotation), Block.NOTIFY_ALL);
            MarkerResidents.refresh(world, paste.pos());
            return Pasted.of(true);
        }
    }

    // ---------------------------------------------------------------- Magnéto

    private static final class Magnetos implements BlockSettings {
        @Override
        public String family() {
            return "magneto";
        }

        @Override
        public boolean handles(BlockState state) {
            return state.getBlock() instanceof StepControllerBlock;
        }

        @Override
        public @Nullable NbtCompound write(ServerWorld world, BlockPos pos, RegistryWrapper.WrapperLookup registries, boolean creative) {
            if (!(world.getBlockEntity(pos) instanceof StepControllerBlockEntity magneto)) return null;
            NbtCompound settings = new NbtCompound();
            settings.putString("Mode", magneto.getMode().name());
            return settings;
        }

        @Override
        public Pasted paste(Paste paste) {
            if (!(paste.world().getBlockEntity(paste.pos()) instanceof StepControllerBlockEntity magneto)) return Pasted.of(false);
            StepPulse.Mode mode = GoalPoleBaseBlockEntity.readEnum(paste.settings(), "Mode", StepPulse.Mode.values(), magneto.getMode());
            if (mode == magneto.getMode()) return Pasted.of(false);
            magneto.selectMode(mode, null);
            return Pasted.of(true);
        }
    }

    // ---------------------------------------------------------------- settings kept in the block state

    /** Settings that are properties of the block state (an antenna's moment, a sign's plate). */
    private static class StateSettings implements BlockSettings {
        private final String family;
        private final Predicate<BlockState> handles;
        private final Property<?>[] properties;

        StateSettings(String family, Predicate<BlockState> handles, Property<?>... properties) {
            this.family = family;
            this.handles = handles;
            this.properties = properties;
        }

        @Override
        public String family() {
            return family;
        }

        @Override
        public boolean handles(BlockState state) {
            return handles.test(state);
        }

        @Override
        public @Nullable NbtCompound write(ServerWorld world, BlockPos pos, RegistryWrapper.WrapperLookup registries, boolean creative) {
            BlockState state = world.getBlockState(pos);
            NbtCompound settings = new NbtCompound();
            for (Property<?> property : properties) if (state.contains(property)) settings.putString(property.getName(), valueName(state, property));
            return settings;
        }

        private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
            return property.name(state.get(property));
        }

        @Override
        public Pasted paste(Paste paste) {
            BlockState before = paste.world().getBlockState(paste.pos()), state = before;
            for (Property<?> property : properties) state = with(state, property, paste.settings().getString(property.getName()));
            state = pasted(state);
            if (state == before) return Pasted.of(false);
            paste.world().setBlockState(paste.pos(), state, Block.NOTIFY_ALL);
            return Pasted.of(true);
        }

        /** The state pasted, adjusted (a waiting the new moment can't keep...). */
        protected BlockState pasted(BlockState state) {
            return state;
        }

        private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String name) {
            return state.contains(property) ? property.parse(name).map(value -> state.with(property, value)).orElse(state) : state;
        }
    }
}
