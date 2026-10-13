package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.ForgeCoreEntity;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaRebirths;
import fr.lordfinn.steveparty.entities.custom.MulaStarEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Playtest of 2026-10-13: hundreds of Mulas (spawn eggs) round a forge, its core blown up by a blast, bursts in waves,
 * a pile of Mulas on one side, and /kill could not stop them coming back. Everything is bounded now.
 */
public class MulaStormGameTests implements SteveGameTest {
    private static final BlockPos FORGE_POS = new BlockPos(4, 1, 4);
    private static final int CROWD = 200;

    /**
     * 200 Mulas round a forge with its core: it holds at most MAX_RESIDENTS, each spectator on its own spot (no pile);
     * a blast does not set the core off, a player's hit does; then at most MAX_RESIDENTS burst, the others stay put.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 500, batchId = "mula_storm")
    public void aCrowdAndACoreBlastStayBounded(TestContext context) {
        ServerWorld world = context.getWorld();
        MulaHomeGameTests.removeOtherForges(context, FORGE_POS);
        context.setBlockState(FORGE_POS, ModBlocks.DICE_FORGE.getDefaultState());
        DiceForgeBlockEntity forge = context.getBlockEntity(FORGE_POS);
        forge.setStack(DiceForgeBlockEntity.CENTER_SLOT, new ItemStack(ModBlocks.GRAVITY_CORE));
        BlockPos home = context.getAbsolutePos(FORGE_POS);
        List<MulaEntity> crowd = new ArrayList<>();
        for (int i = 0; i < CROWD; i++) {
            // a disc round the forge, within its area
            double a = i * 2.39996, r = 2 + (i % 12);
            MulaEntity m = context.spawnEntity(ModEntities.MULA_ENTITY,
                    new BlockPos((int) Math.round(4 + Math.cos(a) * r), 3 + i % 4, (int) Math.round(4 + Math.sin(a) * r)));
            m.setAiDisabled(true);
            crowd.add(m);
        }
        Set<UUID> ids = new HashSet<>();
        crowd.forEach(m -> ids.add(m.getUuid()));
        int settled = DiceForgeBlockEntity.CORE_INSERT_TICKS + 50;
        context.runAtTick(settled, () -> {
            context.assertTrue(forge.isActivated(), "core in");
            List<MulaEntity> residents = crowd.stream().filter(m -> home.equals(m.homeForge())).toList();
            context.assertTrue(!residents.isEmpty() && residents.size() <= DiceForgeBlockEntity.MAX_RESIDENTS,
                    "the forge holds at most " + DiceForgeBlockEntity.MAX_RESIDENTS + ": " + residents.size());
            Set<Integer> spots = new HashSet<>();
            for (MulaEntity m : crowd) {
                if (m.isSpectating()) {
                    context.assertTrue(spots.add(m.danceSlot()), "each spectator on its own spot, not piled: " + m.danceSlot());
                    context.assertTrue(m.danceCount() > m.danceSlot(), "a spot among the spectators: " + m.danceSlot() + "/" + m.danceCount());
                }
            }
            // a blast (another core, TNT, a Nuke) and any other damage leave the core alone
            ForgeCoreEntity core = new ForgeCoreEntity(ModEntities.FORGE_CORE, world);
            core.setForgePos(home);
            core.setPosition(forge.getCoreCenter());
            world.spawnEntity(core);
            context.assertFalse(core.damage(world.getDamageSources().explosion(null, null), 10), "an explosion sets nothing off");
            context.assertFalse(core.damage(world.getDamageSources().generic(), 10), "nor any other damage");
            context.assertTrue(forge.isActivated() && !core.isRemoved(), "core still in");
            // a player's hit does
            PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
            context.assertTrue(core.damage(world.getDamageSources().playerAttack(player), 1), "a player's hit sets it off");
            context.assertFalse(forge.isActivated(), "core blown up");
            world.getEntitiesByClass(ItemEntity.class, new Box(home).expand(24), e -> true).forEach(ItemEntity::discard);
        });
        // the last burst: 2 + 63 x 4 + distance, then the star launch (32)
        context.runAtTick(settled + 340, () -> {
            MulaRebirths rebirths = MulaRebirths.get(world);
            List<MulaRebirths.Entry> mine = rebirths.entries().stream().filter(e -> ids.contains(e.id())).toList();
            context.assertTrue(!mine.isEmpty() && mine.size() <= DiceForgeBlockEntity.MAX_RESIDENTS,
                    "at most " + DiceForgeBlockEntity.MAX_RESIDENTS + " burst: " + mine.size());
            // the others stay put (the blast itself may kill a few next to the core)
            long left = crowd.stream().filter(m -> !m.isRemoved()).count();
            context.assertTrue(left >= CROWD - DiceForgeBlockEntity.MAX_RESIDENTS - 16, "the others stay put: " + left);
            context.assertTrue(crowd.stream().noneMatch(m -> !m.isRemoved() && m.isBursting()), "no burst left pending");
            context.assertTrue(crowd.stream().noneMatch(m -> !m.isRemoved() && home.equals(m.homeForge())), "nobody lives at a forge without its core");
            // what a /kill does, and nothing comes back
            crowd.forEach(Entity::discard);
            mine.forEach(e -> rebirths.remove(e.id()));
            world.getEntitiesByClass(MulaStarEntity.class, new Box(home).expand(60), e -> true).forEach(Entity::discard);
            context.complete();
        });
    }

    /**
     * The rebirth list is bounded: at most MAX_PENDING wild Mulas wait (someone's always), they come back a few per
     * second, a long-overdue wild one in an unloaded chunk is forgotten, and clear() (/mula rebirths clear) stops all.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "mula_storm_rebirths")
    public void theRebirthListIsBounded(TestContext context) {
        ServerWorld world = context.getWorld();
        MulaRebirths rebirths = new MulaRebirths();
        BlockPos at = context.getAbsolutePos(new BlockPos(2, 3, 2));
        long now = world.getTime();
        NbtCompound wild = new NbtCompound();
        context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(2, 3, 2)).saveSelfNbt(wild);
        world.getEntitiesByClass(MulaEntity.class, new Box(at).expand(2), e -> true).forEach(Entity::discard);
        int accepted = 0;
        for (int i = 0; i < MulaRebirths.MAX_PENDING + 50; i++) {
            if (rebirths.add(new MulaRebirths.Entry(UUID.randomUUID(), at.getX(), at.getZ(), at.getY(), now, entityNbt(wild)))) accepted++;
        }
        context.assertTrue(accepted == MulaRebirths.MAX_PENDING && rebirths.size() == MulaRebirths.MAX_PENDING,
                "wild ones capped: " + accepted);
        NbtCompound owned = entityNbt(wild);
        owned.putUuid("Owner", UUID.randomUUID());
        context.assertTrue(rebirths.add(new MulaRebirths.Entry(UUID.randomUUID(), at.getX(), at.getZ(), at.getY(), now, owned)),
                "someone's Mula is always kept");
        // far away, unloaded, long overdue: forgotten when wild
        UUID lost = UUID.randomUUID();
        rebirths.remove(rebirths.entries().get(0).id());
        rebirths.remove(rebirths.entries().get(0).id());
        rebirths.add(new MulaRebirths.Entry(lost, 3_000_000, 3_000_000, 80, now - MulaRebirths.WILD_EXPIRY_TICKS - 1, entityNbt(wild)));
        int before = rebirths.size();
        rebirths.tick(world);
        context.assertTrue(!rebirths.isPending(lost), "a long-overdue wild Mula in an unloaded chunk is forgotten");
        int reborn = before - 1 - rebirths.size();
        context.assertTrue(reborn == MulaRebirths.MAX_PER_CHECK, "a trickle, not a wave: " + reborn);
        rebirths.clear();
        context.assertTrue(rebirths.size() == 0, "cleared");
        world.getEntitiesByClass(MulaEntity.class, new Box(at).expand(8, 40, 8), e -> true).forEach(Entity::discard);
        rebirths.tick(world);
        context.assertTrue(world.getEntitiesByClass(MulaEntity.class, new Box(at).expand(8, 40, 8), e -> true).isEmpty(),
                "nothing comes back after a clear");
        context.complete();
    }

    /** A copy with a fresh UUID (each entry is another Mula). */
    private static NbtCompound entityNbt(NbtCompound template) {
        NbtCompound copy = template.copy();
        copy.remove("UUID");
        return copy;
    }
}
