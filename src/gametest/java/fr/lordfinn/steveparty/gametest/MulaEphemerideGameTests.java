package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaEphemeride;
import fr.lordfinn.steveparty.entities.custom.MulaSpawnSites;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.List;

/** The ephemeride: its chance by moon phase, the guarantee by a max-level forge, the Mula spawn sites. */
public class MulaEphemerideGameTests implements FabricGameTest {

    /** 0 at new moon, rising to the maximum at full moon, symmetric round the cycle. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chanceFollowsTheMoon(TestContext context) {
        context.assertTrue(MulaEphemeride.chance(4) == 0, "new moon: never (by chance)");
        context.assertTrue(MulaEphemeride.chance(0) == MulaEphemeride.MAX_CHANCE, "full moon: the maximum");
        context.assertTrue(MulaEphemeride.chance(1) == MulaEphemeride.chance(7) && MulaEphemeride.chance(2) == MulaEphemeride.chance(6),
                "waxing = waning");
        context.assertTrue(MulaEphemeride.chance(0) > MulaEphemeride.chance(1) && MulaEphemeride.chance(1) > MulaEphemeride.chance(2)
                && MulaEphemeride.chance(2) > MulaEphemeride.chance(3) && MulaEphemeride.chance(3) > 0, "the fuller, the likelier");
        context.complete();
    }

    /** Full moon + a player by a Dice Forge at its highest level: guaranteed; not at another phase, not by a low forge. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = DiceForgeBlockEntity.CORE_INSERT_TICKS + 40, batchId = "ephemeride_forge")
    public void maxForgeAtFullMoonGuaranteesIt(TestContext context) {
        MulaHomeGameTests.removeOtherForges(context, new BlockPos(1, 1, 1));
        ServerWorld world = context.getWorld();
        context.setBlockState(new BlockPos(1, 1, 1), ModBlocks.DICE_FORGE.getDefaultState());
        DiceForgeBlockEntity forge = context.getBlockEntity(new BlockPos(1, 1, 1));
        forge.setStack(DiceForgeBlockEntity.CENTER_SLOT, new ItemStack(ModBlocks.GRAVITY_CORE));
        ServerPlayerEntity player = TestPlayers.mock(context);
        BlockPos at = context.getAbsolutePos(new BlockPos(3, 2, 3));
        player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        try {
            context.assertTrue(!forge.isMaxLevel(), "a core alone is not the max level");
            context.assertTrue(!MulaEphemeride.guaranteed(world, 0, List.of(player)), "not guaranteed by a low forge");
            for (int i = 0; i < DiceForgeBlockEntity.FRAGMENT_SLOTS; i++) {
                forge.setStack(DiceForgeBlockEntity.FIRST_FRAGMENT_SLOT + i, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
            }
            context.assertTrue(forge.isMaxLevel(), "core in, lifted to its highest: max level");
            context.assertTrue(MulaEphemeride.guaranteed(world, 0, List.of(player)), "full moon by a max forge: guaranteed");
            context.assertTrue(!MulaEphemeride.guaranteed(world, 1, List.of(player)), "not guaranteed at another phase");
        } finally {
            forge.clear();
            context.setBlockState(new BlockPos(1, 1, 1), Blocks.AIR);
            world.getServer().getPlayerManager().remove(player);
        }
        context.complete();
    }

    /** A site's Mulas appear once its chunk is loaded, once; the nearest site is found; the sites are saved. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spawnSitesAppearOnceAreFoundAndSaved(TestContext context) {
        ServerWorld world = context.getWorld();
        MulaSpawnSites sites = new MulaSpawnSites();
        BlockPos here = context.getAbsolutePos(new BlockPos(2, 1, 2));
        MulaSpawnSites.Site near = sites.add(here, world.getTime(), new int[]{2, 2, 2, 4});
        MulaSpawnSites.Site far = sites.add(here.add(5000, 0, 0), world.getTime(), new int[]{0, 0, 0});
        context.assertTrue(near != null && far != null, "recorded");
        context.assertTrue(sites.nearest(here.add(10, 0, 3), false).get() == near, "nearest to here");
        context.assertTrue(sites.nearest(here.add(4990, 0, 0), false).get() == far, "nearest to there");

        sites.tick(world);
        Box box = new Box(here).expand(8, 40, 8);
        List<MulaEntity> came = world.getEntitiesByClass(MulaEntity.class, box, m -> true);
        context.assertTrue(came.size() == 4, "the group came down: " + came.size());
        context.assertTrue(came.stream().filter(m -> m.getVariant() == MulaEntity.MulaVariant.GREEN).count() == 3
                && came.stream().anyMatch(m -> m.getVariant() == MulaEntity.MulaVariant.PURPLE), "with its colours");
        context.assertTrue(near.spawned && !far.spawned, "its site is marked; the far one waits for its chunk");
        sites.tick(world);
        context.assertTrue(world.getEntitiesByClass(MulaEntity.class, box, m -> true).size() == 4, "only once");
        context.assertTrue(sites.nearest(here, false).get() == far && sites.nearest(here, true).get() == near,
                "nearest pending / nearest of all");

        NbtCompound nbt = sites.writeNbt(new NbtCompound(), world.getRegistryManager());
        MulaSpawnSites back = MulaSpawnSites.fromNbt(nbt, world.getRegistryManager());
        context.assertTrue(back.sites().size() == 2 && back.pendingCount() == 1, "read back");
        MulaSpawnSites.Site b = back.nearest(here, true).get();
        context.assertTrue(b.pos.equals(here) && b.spawned && b.colours.length == 4 && b.time == near.time, "the same site");
        came.forEach(MulaEntity::discard);
        context.complete();
    }

    /** No endless spawning: a cap on the sites of a dimension (the oldest make room), and small groups. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spawningIsBounded(TestContext context) {
        MulaSpawnSites sites = new MulaSpawnSites();
        int max = MulaSpawnSites.maxSites();
        for (int i = 0; i < max + 10; i++) sites.add(new BlockPos(100000 + i * 1000, 64, 0), 0, new int[]{1});
        context.assertTrue(sites.pendingCount() == max && sites.siteCount() == max, "sites capped: " + sites.siteCount());
        context.assertTrue(sites.byId(10) == null && sites.byId(max + 10) != null, "the latest are the ones kept");
        Random random = Random.create(3);
        for (int i = 0; i < 50; i++) {
            int n = MulaEphemeride.group(random).length;
            context.assertTrue(n >= MulaEphemeride.MIN_GROUP && n <= MulaEphemeride.MAX_GROUP, "a small flock: " + n);
        }
        context.complete();
    }
    /** The stars fly well above the ground, and clear a tall pillar on their way. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void starsFlyAboveTheTrees(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos at = context.getAbsolutePos(new BlockPos(1, 1, 1));
        double ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, at.getX(), at.getZ());
        double open = MulaEphemeride.altitude(world, at.getX() + 0.5, at.getZ() + 0.5, 1, 0, ground);
        context.assertTrue(open >= ground + MulaEphemeride.MIN_ABOVE_GROUND && open <= ground + MulaEphemeride.MAX_ABOVE_GROUND,
                "high above the ground: " + open + " over " + ground);
        BlockPos pillar = new BlockPos(at.getX() + 6, (int) ground, at.getZ()); // a sampled column (every 8 blocks from -90)
        for (int i = 0; i < 24; i++) world.setBlockState(pillar.up(i), Blocks.OAK_LEAVES.getDefaultState());
        try {
            double y = MulaEphemeride.altitude(world, at.getX() + 0.5, at.getZ() + 0.5, 1, 0, ground);
            context.assertTrue(y >= ground + 24 + MulaEphemeride.CLEARANCE, "clears the pillar: " + y);
        } finally {
            for (int i = 0; i < 24; i++) world.setBlockState(pillar.up(i), Blocks.AIR.getDefaultState());
        }
        context.complete();
    }
}
