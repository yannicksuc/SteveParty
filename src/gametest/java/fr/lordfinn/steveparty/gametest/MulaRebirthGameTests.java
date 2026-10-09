package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaRebirths;
import fr.lordfinn.steveparty.entities.custom.MulaStarEntity;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;

/** A burst Mula flies away as a shooting star and is reborn far away: once, the same Mula, even after a restart. */
public class MulaRebirthGameTests implements FabricGameTest {

    /** The higher the arc, the farther: 100 blocks for the lowest, 400 for the highest. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void higherArcGoesFarther(TestContext context) {
        context.assertTrue(MulaStarEntity.distanceFor(MulaStarEntity.MIN_APEX) == 100, "lowest arc: 100 blocks");
        context.assertTrue(MulaStarEntity.distanceFor(MulaStarEntity.MAX_APEX) == 400, "highest arc: 400 blocks");
        context.assertTrue(MulaStarEntity.distanceFor(50) > MulaStarEntity.distanceFor(40), "higher, farther");
        context.complete();
    }

    /** At the pop, the Mula leaves (without dying) as a star, and its rebirth is recorded 100-400 blocks away. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void burstLeavesAsAShootingStar(TestContext context) {
        ServerPlayerEntity owner = TestPlayers.mock(context);
        try {
            MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
            mula.setVariant(MulaEntity.MulaVariant.PURPLE);
            mula.setOwner(owner);
            mula.setHunger(30);
            UUID id = mula.getUuid();
            double x = mula.getX(), z = mula.getZ();
            mula.burstIntoStar();
            context.assertTrue(mula.isRemoved() && mula.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                    "removed without dying");
            ServerWorld world = context.getWorld();
            List<MulaStarEntity> stars = world.getEntitiesByClass(MulaStarEntity.class, new Box(mula.getBlockPos()).expand(3),
                    s -> true);
            context.assertTrue(stars.size() == 1 && stars.get(0).getVariant() == MulaEntity.MulaVariant.PURPLE,
                    "a shooting star of its colour");
            MulaRebirths.Entry entry = MulaRebirths.get(world).entries().stream().filter(e -> e.id().equals(id))
                    .findFirst().orElse(null);
            context.assertTrue(entry != null, "its rebirth is recorded");
            double distance = Math.hypot(entry.x() + 0.5 - x, entry.z() + 0.5 - z);
            context.assertTrue(distance >= 99 && distance <= 401, "reborn 100-400 blocks away: " + distance);
            context.assertTrue(entry.due() > world.getTime(), "when its star lands");
            context.assertTrue(entry.mula().getInt("Variant") == MulaEntity.MulaVariant.PURPLE.getId()
                    && entry.mula().getInt("Hunger") == 0 && entry.mula().containsUuid("Owner"),
                    "same colour and owner, empty belly");
            stars.forEach(Entity::discard);
            MulaRebirths.get(world).remove(id);
        } finally {
            TestPlayers.remove(context, owner);
        }
        context.complete();
    }

    /** Due and its chunk loaded: reborn once (same UUID, colour, owner), never twice; not before its time. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rebornOnceWhenDueAndLoaded(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity owner = TestPlayers.mock(context);
        try {
            MulaEntity model = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
            model.setVariant(MulaEntity.MulaVariant.GREEN);
            model.setOwner(owner);
            NbtCompound saved = new NbtCompound();
            model.saveSelfNbt(saved);
            UUID id = model.getUuid();
            model.discard();
            BlockPos target = context.getAbsolutePos(new BlockPos(2, 1, 2));
            MulaRebirths rebirths = MulaRebirths.get(world);

            rebirths.add(new MulaRebirths.Entry(id, target.getX(), target.getZ(), target.getY() + 2,
                    world.getTime() + 1000, saved));
            rebirths.tick(world);
            context.assertTrue(world.getEntity(id) == null, "not before its star has landed");

            rebirths.add(new MulaRebirths.Entry(id, target.getX(), target.getZ(), target.getY() + 2,
                    world.getTime(), saved));
            rebirths.tick(world);
            Entity reborn = world.getEntity(id);
            context.assertTrue(reborn instanceof MulaEntity m && m.getVariant() == MulaEntity.MulaVariant.GREEN
                    && owner.getUuid().equals(m.getOwnerUuid()), "reborn: same Mula, same colour, same owner");
            context.assertTrue(!rebirths.isPending(id), "done");
            context.assertTrue(Math.abs(reborn.getX() - (target.getX() + 0.5)) < 0.01
                    && Math.abs(reborn.getZ() - (target.getZ() + 0.5)) < 0.01, "where its star landed");

            // the same rebirth again (a stale entry): nothing more
            rebirths.add(new MulaRebirths.Entry(id, target.getX(), target.getZ(), target.getY() + 2,
                    world.getTime(), saved));
            rebirths.tick(world);
            long copies = world.getEntitiesByClass(MulaEntity.class, new Box(target).expand(64),
                    m -> m.getUuid().equals(id)).size();
            context.assertTrue(copies == 1, "never twice: " + copies);
            reborn.discard();
        } finally {
            world.getServer().getPlayerManager().remove(owner);
        }
        context.complete();
    }

    /** The pending rebirths are saved with the world and read back the same. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rebirthsSurviveARestart(TestContext context) {
        ServerWorld world = context.getWorld();
        MulaRebirths before = new MulaRebirths();
        NbtCompound mula = new NbtCompound();
        mula.putString("id", "steveparty:mula");
        mula.putInt("Variant", 3);
        UUID id = UUID.randomUUID();
        before.add(new MulaRebirths.Entry(id, 1234, -567, 80.5, 99999, mula));
        NbtCompound saved = before.writeNbt(new NbtCompound(), world.getRegistryManager());
        MulaRebirths after = MulaRebirths.fromNbt(saved, world.getRegistryManager());
        context.assertTrue(after.entries().size() == 1, "one pending rebirth");
        MulaRebirths.Entry e = after.entries().get(0);
        context.assertTrue(e.id().equals(id) && e.x() == 1234 && e.z() == -567 && e.y() == 80.5 && e.due() == 99999
                && e.mula().getInt("Variant") == 3, "read back the same: " + e);
        context.complete();
    }

    /** An old copy of a Mula on its way to be reborn (crash between saves) is removed when it loads. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oldCopyOfAPendingMulaIsRemoved(TestContext context) {
        ServerWorld world = context.getWorld();
        MulaEntity mula = ModEntities.MULA_ENTITY.create(world);
        UUID id = mula.getUuid();
        NbtCompound saved = new NbtCompound();
        mula.saveSelfNbt(saved);
        MulaRebirths rebirths = MulaRebirths.get(world);
        rebirths.add(new MulaRebirths.Entry(id, 0, 0, 80, Long.MAX_VALUE, saved));
        BlockPos at = context.getAbsolutePos(new BlockPos(1, 3, 1));
        mula.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        world.spawnEntity(mula);
        context.waitAndRun(2, () -> {
            context.assertTrue(mula.isRemoved(), "the stale copy is removed");
            MulaRebirths.get(world).remove(id);
            context.complete();
        });
    }

    /** Black Mulas: one fragment when they burst from food (the others still 64), and a star 500-2000 blocks away. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void blackMulasDropOneFragmentAndFlyFarther(TestContext context) {
        context.assertTrue(MulaStarEntity.distanceFor(MulaStarEntity.MIN_APEX, MulaEntity.MulaVariant.BLACK) == 500
                && MulaStarEntity.distanceFor(MulaStarEntity.MAX_APEX, MulaEntity.MulaVariant.BLACK) == 2000, "black: 500-2000");
        context.assertTrue(MulaStarEntity.distanceFor(MulaStarEntity.MIN_APEX, MulaEntity.MulaVariant.BLUE) == 100
                && MulaStarEntity.distanceFor(MulaStarEntity.MAX_APEX, MulaEntity.MulaVariant.RED) == 400, "others: 100-400");
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = TestPlayers.mock(context, net.minecraft.world.GameMode.SURVIVAL);
        MulaEntity black = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        black.setVariant(MulaEntity.MulaVariant.BLACK);
        black.setHunger(MulaEntity.MAX_HUNGER - 1);
        black.setAiDisabled(true);
        MulaEntity blue = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(6, 3, 6));
        blue.setVariant(MulaEntity.MulaVariant.BLUE);
        blue.setHunger(MulaEntity.MAX_HUNGER - 1);
        blue.setAiDisabled(true);
        try {
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.COOKED_BEEF, 4));
            black.interactMob(player, net.minecraft.util.Hand.MAIN_HAND);
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.COD, 4));
            blue.interactMob(player, net.minecraft.util.Hand.MAIN_HAND);
        } finally {
            world.getServer().getPlayerManager().remove(player);
        }
        UUID id = black.getUuid();
        context.runAtTick(60, () -> {
            int blackCount = 0, blueCount = 0;
            for (var item : world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new Box(context.getAbsolutePos(new BlockPos(3, 3, 3))).expand(12), e -> true)) {
                if (item.getStack().isOf(fr.lordfinn.steveparty.items.ModItems.BLACK_STAR_FRAGMENT)) blackCount += item.getStack().getCount();
                if (item.getStack().isOf(fr.lordfinn.steveparty.items.ModItems.BLUE_STAR_FRAGMENT)) blueCount += item.getStack().getCount();
                item.discard();
            }
            context.assertTrue(blackCount == 1, "a black burst drops exactly 1 fragment: " + blackCount);
            context.assertTrue(blueCount == 64, "the others still 64: " + blueCount);
            MulaRebirths.Entry e = MulaRebirths.get(world).entries().stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
            context.assertTrue(e != null, "black rebirth recorded");
            double d = Math.hypot(e.x() + 0.5 - (context.getAbsolutePos(new BlockPos(1, 3, 1)).getX() + 0.5),
                    e.z() + 0.5 - (context.getAbsolutePos(new BlockPos(1, 3, 1)).getZ() + 0.5));
            context.assertTrue(d >= 495 && d <= 2005, "black reborn 500-2000 blocks away: " + d);
            MulaRebirths.get(world).entries().forEach(x -> { if (x.id().equals(id) || x.id().equals(blue.getUuid())) MulaRebirths.get(world).remove(x.id()); });
            world.getEntitiesByClass(MulaStarEntity.class, new Box(context.getAbsolutePos(new BlockPos(3, 3, 3))).expand(40), x -> true).forEach(Entity::discard);
            context.complete();
        });
    }
}
