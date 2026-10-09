package fr.lordfinn.steveparty.entities.custom.mistigri;

import fr.lordfinn.steveparty.entities.PetSlots;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity.Action;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChiseledBookshelfBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChiseledBookshelfBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/** What the Mistigri does by himself: a cat's life. Server side. */
public final class MistigriGoals {
    /** How far he looks for something to knock off, or a chest to sit on (blocks). */
    static final int SEARCH = 10;
    /** Between two things knocked off: one to two minutes (ticks). */
    static final int SWAT_COOLDOWN_MIN = 1200, SWAT_COOLDOWN_MAX = 2400;

    private MistigriGoals() {
    }

    /** Angry, or in the middle of an act: he stays where he is (angry: facing whatever made him so). */
    static final class Hold extends Goal {
        private final MistigriEntity mistigri;

        Hold(MistigriEntity mistigri) {
            this.mistigri = mistigri;
            setControls(EnumSet.of(Control.MOVE, Control.JUMP));
        }

        @Override
        public boolean canStart() {
            return mistigri.isAngry() || mistigri.isActing() && mistigri.getAction() != Action.LEAP;
        }

        @Override
        public void start() {
            mistigri.getNavigation().stop();
        }
    }

    /**
     * A tamed one follows his owner at a little distance, each of his owner's Mistigris its own place behind them
     * (PetSlots); far behind, he pops by them (vanilla's teleport, PetTeleports beyond).
     */
    static final class FollowOwner extends Goal {
        private final MistigriEntity mistigri;
        private final PetSlots.Group mates;
        private @Nullable PlayerEntity owner;
        private int repath;

        FollowOwner(MistigriEntity mistigri) {
            this.mistigri = mistigri;
            this.mates = new PetSlots.Group(mistigri);
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        private boolean far(double distance) {
            return owner != null && mistigri.squaredDistanceTo(owner) > distance * distance;
        }

        @Override
        public boolean canStart() {
            if (!mistigri.isTamed() || mistigri.isSitting() || mistigri.isBoardActor() || mistigri.isAngry()
                    || mistigri.isLeashed()) return false;
            owner = mistigri.getOwner() instanceof PlayerEntity player && !player.isSpectator() ? player : null;
            return owner != null && owner.getWorld() == mistigri.getWorld() && far(6);
        }

        @Override
        public boolean shouldContinue() {
            return owner != null && owner.isAlive() && !mistigri.isSitting() && !mistigri.isAngry() && far(3.0);
        }

        @Override
        public void start() {
            repath = 0;
            mistigri.setLoafing(false);
            mistigri.setStaring(false);
        }

        @Override
        public void stop() {
            owner = null;
            mistigri.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (owner == null) return;
            mistigri.getLookControl().lookAt(owner, 10.0f, mistigri.getMaxLookPitchChange());
            if (mistigri.shouldTryTeleportToOwner()) {
                mistigri.tryTeleportToOwner();
                return;
            }
            if (--repath > 0) return;
            repath = 10;
            PlayerEntity owner = this.owner;
            mates.refresh(16, 20, entity -> entity instanceof MistigriEntity mate && mate.isOwner(owner) && !mate.isSitting());
            // behind them, fanned out side by side
            float yaw = owner.getYaw() * MathHelper.RADIANS_PER_DEGREE;
            Vec3d back = new Vec3d(MathHelper.sin(yaw), 0, -MathHelper.cos(yaw)).multiply(2.2);
            Vec3d side = new Vec3d(-back.z, 0, back.x).normalize();
            double offset = (mates.index() - (mates.count() - 1) / 2.0) * 1.8;
            Vec3d spot = mates.pushApart(owner.getPos().add(back).add(side.multiply(offset)), 1.6);
            mistigri.getNavigation().startMovingTo(spot.x, spot.y, spot.z, far(10) ? 1.3 : 1.0);
        }
    }

    /**
     * Knocks something off with a paw, once in a while: the item in an item frame within reach of a paw, or a book out
     * of a chiseled bookshelf. Walks there, rears up, swats: it falls to the ground.
     */
    static final class KnockOff extends Goal {
        private final MistigriEntity mistigri;
        private @Nullable ItemFrameEntity frame;
        private @Nullable BlockPos shelf;
        private int ticks, swatAt;

        KnockOff(MistigriEntity mistigri) {
            this.mistigri = mistigri;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!mistigri.isFree() || !(mistigri.getWorld() instanceof ServerWorld world)) return false;
            if (world.getTime() < mistigri.nextSwatTime || mistigri.getRandom().nextInt(20) != 0) return false;
            frame = findFrame(world, mistigri);
            shelf = frame == null ? findShelf(world, mistigri) : null;
            return frame != null || shelf != null;
        }

        @Override
        public boolean shouldContinue() {
            if (ticks > 200 || mistigri.isAngry() || mistigri.isSitting()) return false;
            if (swatAt > 0) return ticks < swatAt + 12;
            return frame != null ? frame.isAlive() && !frame.getHeldItemStack().isEmpty() : shelf != null;
        }

        @Override
        public void start() {
            ticks = 0;
            swatAt = 0;
            mistigri.setLoafing(false);
            Vec3d target = target();
            mistigri.getNavigation().startMovingTo(target.x, target.y, target.z, 0.9);
        }

        @Override
        public void stop() {
            frame = null;
            shelf = null;
            mistigri.getNavigation().stop();
        }

        private Vec3d target() {
            return frame != null ? frame.getPos() : Vec3d.ofCenter(shelf);
        }

        @Override
        public void tick() {
            ticks++;
            Vec3d target = target();
            mistigri.getLookControl().lookAt(target.x, target.y, target.z);
            if (swatAt == 0) {
                double dx = target.x - mistigri.getX(), dz = target.z - mistigri.getZ();
                if (dx * dx + dz * dz < 2.6 * 2.6) {
                    mistigri.getNavigation().stop();
                    float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90f;
                    mistigri.setYaw(yaw);
                    mistigri.setBodyYaw(yaw);
                    mistigri.setHeadYaw(yaw);
                    mistigri.act(Action.SWAT);
                    swatAt = ticks;
                } else if (ticks % 20 == 0) {
                    mistigri.getNavigation().startMovingTo(target.x, target.y, target.z, 0.9);
                }
            } else if (ticks == swatAt + 10 && mistigri.getWorld() instanceof ServerWorld world) {
                if (frame != null) knock(world, frame);
                else knock(world, shelf);
                mistigri.nextSwatTime = world.getTime() + MathHelper.nextInt(mistigri.getRandom(), SWAT_COOLDOWN_MIN, SWAT_COOLDOWN_MAX);
            }
        }
    }

    /** An item frame holding something, at most a paw's reach above his feet (2.5 blocks), the nearest. */
    public static @Nullable ItemFrameEntity findFrame(ServerWorld world, MistigriEntity mistigri) {
        ItemFrameEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemFrameEntity frame : world.getEntitiesByClass(ItemFrameEntity.class, mistigri.getBoundingBox().expand(SEARCH),
                frame -> frame.isAlive() && !frame.getHeldItemStack().isEmpty())) {
            double dy = frame.getY() - mistigri.getY();
            if (dy < -0.5 || dy > 2.5 || frame.getHorizontalFacing() == Direction.DOWN) continue;
            double distance = frame.squaredDistanceTo(mistigri);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = frame;
            }
        }
        return best;
    }

    /** A chiseled bookshelf holding a book, within a paw's reach above his feet, the nearest. */
    static @Nullable BlockPos findShelf(ServerWorld world, MistigriEntity mistigri) {
        BlockPos feet = mistigri.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(feet.add(-SEARCH, 0, -SEARCH), feet.add(SEARCH, 2, SEARCH))) {
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof ChiseledBookshelfBlock)) continue;
            if (!(world.getBlockEntity(pos) instanceof ChiseledBookshelfBlockEntity shelf) || shelf.isEmpty()) continue;
            double distance = pos.getSquaredDistance(feet);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.toImmutable();
            }
        }
        return best;
    }

    /** Swats the item out of {@code frame}: it drops at the frame's foot. */
    public static void knock(ServerWorld world, ItemFrameEntity frame) {
        ItemStack held = frame.getHeldItemStack().copy();
        if (held.isEmpty()) return;
        frame.setHeldItemStack(ItemStack.EMPTY);
        drop(world, frame.getPos(), held);
        world.playSound(null, frame.getBlockPos(), SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /** Swats one book out of the chiseled bookshelf at {@code pos} (a random slot holding one). */
    public static void knock(ServerWorld world, @Nullable BlockPos pos) {
        if (pos == null || !(world.getBlockEntity(pos) instanceof ChiseledBookshelfBlockEntity shelf)) return;
        int slot = -1, found = 0;
        for (int i = 0; i < shelf.size(); i++) {
            if (!shelf.getStack(i).isEmpty() && world.getRandom().nextInt(++found) == 0) slot = i;
        }
        if (slot < 0) return;
        ItemStack book = shelf.removeStack(slot, 1);
        BlockState state = world.getBlockState(pos);
        Direction facing = state.contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING) ? state.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING) : Direction.NORTH;
        drop(world, Vec3d.ofCenter(pos).add(Vec3d.of(facing.getVector()).multiply(0.7)), book);
        world.playSound(null, pos, SoundEvents.BLOCK_CHISELED_BOOKSHELF_PICKUP, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    private static void drop(ServerWorld world, Vec3d at, ItemStack stack) {
        ItemEntity item = new ItemEntity(world, at.x, at.y, at.z, stack);
        item.setVelocity((world.getRandom().nextDouble() - 0.5) * 0.1, 0.1, (world.getRandom().nextDouble() - 0.5) * 0.1);
        item.setToDefaultPickupDelay();
        world.spawnEntity(item);
    }

    /**
     * Sits on a chest (or a barrel), as cats do: walks to it, hops on, and loafs there a long while; it won't open under
     * him (MistigriJinx).
     */
    static final class SitOnChest extends Goal {
        private final MistigriEntity mistigri;
        private @Nullable BlockPos chest;
        private int ticks, stay;
        private boolean seated;

        SitOnChest(MistigriEntity mistigri) {
            this.mistigri = mistigri;
            setControls(EnumSet.of(Control.MOVE, Control.JUMP));
        }

        @Override
        public boolean canStart() {
            if (!mistigri.isFree() || mistigri.getRandom().nextInt(200) != 0) return false;
            if (!(mistigri.getWorld() instanceof ServerWorld world)) return false;
            chest = findChest(world, mistigri);
            return chest != null;
        }

        @Override
        public boolean shouldContinue() {
            if (chest == null || mistigri.isAngry() || mistigri.isSitting() || mistigri.isActing() && !seated) return false;
            if (!MistigriJinx.isSeat(mistigri.getWorld().getBlockState(chest).getBlock())) return false;
            return seated ? ticks < stay && mistigri.chest() != null : ticks < 200;
        }

        @Override
        public void start() {
            ticks = 0;
            seated = false;
            stay = MathHelper.nextInt(mistigri.getRandom(), 600, 1600);
            mistigri.getNavigation().startMovingTo(chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5, 0.9);
        }

        @Override
        public void stop() {
            if (seated) mistigri.setLoafing(false);
            chest = null;
            seated = false;
        }

        @Override
        public void tick() {
            ticks++;
            if (seated || chest == null) return;
            Vec3d top = new Vec3d(chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5);
            if (mistigri.getBlockPos().down().equals(chest) && mistigri.isOnGround()) {
                mistigri.getNavigation().stop();
                mistigri.loafOn(chest);
                seated = true;
                ticks = 0;
                return;
            }
            double distance = mistigri.getPos().distanceTo(top);
            if (distance < 2.4 && mistigri.isOnGround()) { // a hop up
                mistigri.getNavigation().stop();
                Vec3d to = top.subtract(mistigri.getPos());
                mistigri.setVelocity(to.x * 0.22, 0.48, to.z * 0.22);
                mistigri.velocityDirty = true;
            } else if (ticks % 20 == 0) {
                mistigri.getNavigation().startMovingTo(top.x, top.y, top.z, 0.9);
            }
        }
    }

    /** A chest, barrel or ender chest with room above it and nobody on it, within {@link #SEARCH} blocks. */
    static @Nullable BlockPos findChest(ServerWorld world, MistigriEntity mistigri) {
        BlockPos feet = mistigri.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(feet.add(-SEARCH, -2, -SEARCH), feet.add(SEARCH, 2, SEARCH))) {
            if (!MistigriJinx.isSeat(world.getBlockState(pos).getBlock())) continue;
            if (!world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()
                    || !world.getBlockState(pos.up(2)).getCollisionShape(world, pos.up(2)).isEmpty()) continue;
            if (MistigriJinx.sitter(world, pos) != null) continue;
            double distance = pos.getSquaredDistance(feet);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.toImmutable();
            }
        }
        return best;
    }

    /**
     * A wild one picks a player now and then and follows them about at a few blocks, staring at them: when they stop,
     * he stops too, and stares.
     */
    static final class StareFollow extends Goal {
        private final MistigriEntity mistigri;
        private @Nullable PlayerEntity target;
        private int ticks, length;

        StareFollow(MistigriEntity mistigri) {
            this.mistigri = mistigri;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (mistigri.isTamed() || !mistigri.isFree() || mistigri.getRandom().nextInt(160) != 0) return false;
            target = mistigri.getWorld().getClosestPlayer(mistigri, 12.0);
            return target != null && !target.isSpectator();
        }

        @Override
        public boolean shouldContinue() {
            return target != null && target.isAlive() && !target.isSpectator() && ticks < length && !mistigri.isAngry()
                    && !mistigri.isActing() && mistigri.squaredDistanceTo(target) < 20 * 20;
        }

        @Override
        public void start() {
            ticks = 0;
            length = MathHelper.nextInt(mistigri.getRandom(), 300, 700);
            mistigri.setLoafing(false);
        }

        @Override
        public void stop() {
            target = null;
            mistigri.setStaring(false);
            mistigri.getNavigation().stop();
        }

        @Override
        public void tick() {
            ticks++;
            if (target == null) return;
            mistigri.getLookControl().lookAt(target, 30.0f, 30.0f);
            double distance = mistigri.distanceTo(target);
            if (distance > 5.0) {
                mistigri.setStaring(false);
                if (ticks % 10 == 0) mistigri.getNavigation().startMovingTo(target, 0.8);
            } else if (distance < 3.0) {
                mistigri.getNavigation().stop();
                mistigri.setStaring(true);
            } else if (mistigri.getNavigation().isIdle()) {
                mistigri.setStaring(true);
            }
        }
    }

    /**
     * Standing about with nothing to do, now and then: a groom, a stretch, a yawn, or a nap in a loaf. Tamed ones too
     * (while they wait by their owner).
     */
    static final class IdleActs extends Goal {
        private final MistigriEntity mistigri;
        private int nap;

        IdleActs(MistigriEntity mistigri) {
            this.mistigri = mistigri;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return mistigri.isFree() && !mistigri.isLoafing() && mistigri.getNavigation().isIdle()
                    && mistigri.getRandom().nextInt(240) == 0;
        }

        @Override
        public boolean shouldContinue() {
            return nap > 0 && mistigri.isLoafing() && !mistigri.isAngry() && !mistigri.isSitting();
        }

        @Override
        public void start() {
            nap = 0;
            switch (mistigri.getRandom().nextInt(5)) {
                case 0 -> mistigri.act(Action.GROOM);
                case 1 -> mistigri.act(Action.STRETCH);
                case 2 -> {
                    mistigri.act(Action.YAWN);
                    mistigri.playSound(ModSounds.MISTIGRI_MEOW, 0.5f, 0.8f);
                }
                default -> {
                    mistigri.loafOn(null);
                    nap = MathHelper.nextInt(mistigri.getRandom(), 300, 900);
                }
            }
        }

        @Override
        public void tick() {
            if (nap > 0) nap--;
        }

        @Override
        public void stop() {
            if (mistigri.isLoafing() && mistigri.chest() == null) mistigri.setLoafing(false);
            nap = 0;
        }
    }
}
