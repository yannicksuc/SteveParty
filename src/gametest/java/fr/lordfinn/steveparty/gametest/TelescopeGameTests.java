package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.TelescopeBlock;
import fr.lordfinn.steveparty.blocks.custom.TelescopeBlockEntity;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaSpawnSites;
import fr.lordfinn.steveparty.entities.custom.MulaStarEntity;
import fr.lordfinn.steveparty.payloads.custom.TelescopePayloads;
import fr.lordfinn.steveparty.telescope.TelescopeMath;
import fr.lordfinn.steveparty.telescope.TelescopeService;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;

/**
 * The Telescope: the nights it lists (in range, not yet found, per player), what a find changes (that player's guide
 * stars and nothing else), the tracking gauge and the numbers of the replay and of the guide star.
 */
public class TelescopeGameTests implements FabricGameTest {

    /** Held well centred the gauge fills fast, at the edge slowly, off the star it drains slowly; it stays in 0..1. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gaugeFillsWhileTrackingAndDrainsOff(TestContext context) {
        float g = 0;
        int ticks = 0;
        while (g < 1 && ticks < 10_000) {
            g = TelescopeMath.step(g, 0);
            ticks++;
        }
        context.assertTrue(Math.abs(ticks - TelescopeMath.FILL_CENTRED_TICKS) <= 1, "centred: full in " + ticks + " ticks");
        context.assertTrue(TelescopeMath.step(1f, 0) == 1f, "never over full");

        g = 0;
        ticks = 0;
        while (g < 1 && ticks < 10_000) {
            g = TelescopeMath.step(g, TelescopeMath.TRACK_DEGREES);
            ticks++;
        }
        context.assertTrue(Math.abs(ticks - TelescopeMath.FILL_EDGE_TICKS) <= 2, "at the edge: full in " + ticks + " ticks");
        context.assertTrue(TelescopeMath.step(0.5f, 1) > TelescopeMath.step(0.5f, 6), "the better centred, the faster");
        context.assertTrue(TelescopeMath.step(0.5f, TelescopeMath.CENTRE_DEGREES) == TelescopeMath.step(0.5f, 0),
                "anywhere in the centre is as good");

        // forgiving: off the star (too far, or no star in the sky) it only drains slowly
        float off = TelescopeMath.step(0.5f, TelescopeMath.TRACK_DEGREES + 0.1), none = TelescopeMath.step(0.5f, -1);
        context.assertTrue(off < 0.5f && off == none, "drains off the star");
        context.assertTrue(0.5f - off < TelescopeMath.step(0.5f, TelescopeMath.TRACK_DEGREES) - 0.5f,
                "drains slower than it fills");
        g = 1;
        ticks = 0;
        while (g > 0 && ticks < 10_000) {
            g = TelescopeMath.step(g, -1);
            ticks++;
        }
        context.assertTrue(Math.abs(ticks - TelescopeMath.DRAIN_TICKS) <= 2, "empty in " + ticks + " ticks");
        context.assertTrue(TelescopeMath.step(0f, -1) == 0f, "never under empty");
        context.assertTrue(TelescopeMath.tracking(3) && !TelescopeMath.tracking(8) && !TelescopeMath.tracking(-1), "tracking");
        context.complete();
    }

    /** A replayed star stays over the horizon, comes from behind the watcher and vanishes towards the site; it loops. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void replayedStarsHeadForTheSite(TestContext context) {
        double[] p = new double[3];
        double dirX = 0.6, dirZ = -0.8;
        for (int site = 1; site <= 20; site++) {
            for (int star = 0; star < TelescopeMath.STARS; star++) {
                double side = TelescopeMath.side(site, star), height = TelescopeMath.height(site, star);
                context.assertTrue(Math.abs(side) >= 25 && Math.abs(side) <= 85 && height >= 80 && height <= 110,
                        "a way in the sky: side " + side + ", height " + height);
                context.assertTrue(side == TelescopeMath.side(site, star), "the same every time");
                for (double u = 0; u <= 1; u += 0.05) {
                    TelescopeMath.direction(dirX, dirZ, side, height, u, p);
                    double length = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
                    context.assertTrue(Math.abs(length - 1) < 1e-6, "a direction");
                    context.assertTrue(p[1] > Math.sin(Math.toRadians(12)), "over the horizon: " + p[1]);
                }
                TelescopeMath.direction(dirX, dirZ, side, height, 0, p);
                context.assertTrue(p[0] * dirX + p[2] * dirZ < -0.7, "it comes from behind");
                TelescopeMath.direction(dirX, dirZ, side, height, 1, p);
                context.assertTrue(p[0] * dirX + p[2] * dirZ > 0.7, "it vanishes towards the site");
            }
        }
        context.assertTrue(TelescopeMath.fade(0) == 0 && TelescopeMath.fade(1) == 0 && TelescopeMath.fade(0.5) == 1
                && TelescopeMath.fade(-1) == 0, "lit in the middle of its flight only");
        context.assertTrue(TelescopeMath.progress(0, 0) == 0 && TelescopeMath.progress(0, TelescopeMath.FLIGHT_TICKS) == 1
                && TelescopeMath.progress(0, TelescopeMath.FLIGHT_TICKS + 1) < 0, "one flight, then out of the sky");
        context.assertTrue(TelescopeMath.progress(0, TelescopeMath.LOOP_TICKS + 34) == TelescopeMath.progress(0, 34), "it loops");
        context.assertTrue(TelescopeMath.progress(1, 0) != TelescopeMath.progress(0, 0), "the stars follow one another");
        context.assertTrue(Math.abs(TelescopeMath.angle(1, 0, 0, 0, 1, 0) - 90) < 1e-6 && TelescopeMath.angle(0, 1, 0, 0, 1, 0) < 1e-6, "angles");
        context.complete();
    }

    /** The guide star: low and dim far away, high and bright near; its direction and distance in words. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void guideStarTellsDistanceAndDirection(TestContext context) {
        context.assertTrue(TelescopeMath.guideElevation(0) > 75 && TelescopeMath.guideElevation(1000) < 20
                && TelescopeMath.guideElevation(1000) > 10, "nearly overhead there, low but over the horizon far away");
        double last = 91;
        float lastBright = 2;
        for (int d = 0; d <= 1200; d += 50) {
            double e = TelescopeMath.guideElevation(d);
            float b = TelescopeMath.guideBrightness(d);
            context.assertTrue(e < last && b < lastBright && b >= 0.5f && b <= 1f, "lower and dimmer with the distance");
            last = e;
            lastBright = b;
        }
        context.assertTrue(TelescopeMath.band(20) == 0 && TelescopeMath.band(149) == 0 && TelescopeMath.band(150) == 1
                && TelescopeMath.band(449) == 1 && TelescopeMath.band(450) == 2 && TelescopeMath.band(5000) == 2, "near, far, very far");
        // north is -z, east is +x
        context.assertTrue(TelescopeMath.cardinal(0, -10) == 0 && TelescopeMath.cardinal(10, -10) == 1
                && TelescopeMath.cardinal(10, 0) == 2 && TelescopeMath.cardinal(10, 10) == 3 && TelescopeMath.cardinal(0, 10) == 4
                && TelescopeMath.cardinal(-10, 10) == 5 && TelescopeMath.cardinal(-10, 0) == 6 && TelescopeMath.cardinal(-10, -10) == 7,
                "the eight compass points");
        context.assertTrue(TelescopeMath.cardinal(1, -10) == 0 && TelescopeMath.cardinal(-1, -10) == 0, "roughly north is north");
        context.assertTrue(TelescopeMath.isNightTime(13000) && TelescopeMath.isNightTime(18000) && TelescopeMath.isNightTime(24000 * 7 + 22999)
                && !TelescopeMath.isNightTime(12000) && !TelescopeMath.isNightTime(23000) && !TelescopeMath.isNightTime(6000), "the night");
        context.complete();
    }

    /** The sites a telescope lists: in range, the latest first, capped; and what each player knows is his own, and saved. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sitesAreListedInRangeAndKnownPerPlayer(TestContext context) {
        ServerWorld world = context.getWorld();
        MulaSpawnSites sites = new MulaSpawnSites();
        BlockPos here = new BlockPos(0, 64, 0);
        UUID ann = UUID.randomUUID(), bob = UUID.randomUUID();
        MulaSpawnSites.Site old = sites.add(new BlockPos(300, 64, 0), 24000L * 3 + 14000, 3, new int[]{1, 1, 1});
        MulaSpawnSites.Site recent = sites.add(new BlockPos(0, 64, -900), 24000L * 9 + 15000, 9, new int[]{2, 2, 4});
        MulaSpawnSites.Site tooFar = sites.add(new BlockPos(800, 64, 800), 24000L * 5, 5, new int[]{0});
        context.assertTrue(old != null && recent != null && tooFar != null && recent.day == 9, "recorded with their night");

        List<MulaSpawnSites.Site> listed = sites.unfoundNear(ann, here, TelescopeService.RANGE, 8);
        context.assertTrue(listed.size() == 2 && listed.get(0) == recent && listed.get(1) == old, "in range only, the latest first");
        context.assertTrue(sites.unfoundNear(ann, here, TelescopeService.RANGE, 1).equals(List.of(recent)), "capped");
        context.assertTrue(sites.unfoundNear(ann, here.add(600, 0, 600), TelescopeService.RANGE, 8).contains(tooFar), "in range from elsewhere");

        // Ann finds the recent one: hers alone
        context.assertTrue(sites.markFound(ann, recent.id) && !sites.markFound(ann, recent.id), "found once");
        context.assertTrue(!sites.markFound(ann, 9999), "an unknown site is not found");
        context.assertTrue(sites.unfoundNear(ann, here, TelescopeService.RANGE, 8).equals(List.of(old)), "no longer listed for her");
        context.assertTrue(sites.unfoundNear(bob, here, TelescopeService.RANGE, 8).size() == 2, "still listed for Bob");
        context.assertTrue(sites.guides(ann).equals(List.of(recent)) && sites.guides(bob).isEmpty(), "her guide star, not his");
        context.assertTrue(!recent.spawned && sites.pendingCount() == 3, "the site itself is untouched");

        // Bob walks to the old one without a telescope: visited, for him
        context.assertTrue(!sites.visitAround(bob, new BlockPos(310, 70, 5), TelescopeService.REACH), "no guide star of his went out");
        context.assertTrue(sites.hasVisited(bob, old.id) && !sites.hasVisited(ann, old.id), "visited by him only");
        context.assertTrue(sites.unfoundNear(bob, here, TelescopeService.RANGE, 8).equals(List.of(recent)), "a visited site is not listed");
        context.assertTrue(!sites.markFound(bob, old.id), "nor found afterwards");
        context.assertTrue(!sites.visitAround(bob, new BlockPos(340, 70, 0), TelescopeService.REACH)
                && !sites.hasVisited(bob, tooFar.id), "only the sites within reach");

        // Saved and read back, per player
        MulaSpawnSites back = MulaSpawnSites.fromNbt(sites.writeNbt(new NbtCompound(), world.getRegistryManager()), world.getRegistryManager());
        context.assertTrue(back.hasFound(ann, recent.id) && !back.hasFound(bob, recent.id) && back.hasVisited(bob, old.id)
                && !back.hasVisited(ann, old.id), "what each knows is read back");
        context.assertTrue(back.byId(recent.id).day == 9 && back.byId(old.id).day == 3, "and the nights");
        context.assertTrue(back.guides(ann).size() == 1 && back.guides(ann).get(0).id == recent.id, "her guide star is still there");

        // Ann reaches her site: the star goes out, and stays out
        context.assertTrue(back.visitAround(ann, recent.pos.add(10, 0, -10), TelescopeService.REACH), "her guide star goes out");
        context.assertTrue(back.guides(ann).isEmpty() && back.hasVisited(ann, recent.id) && !back.hasFound(ann, recent.id), "visited");
        context.assertTrue(back.unfoundNear(bob, here, TelescopeService.RANGE, 8).size() == 1, "Bob can still find it");
        context.assertTrue(back.remove(recent.id) && back.byId(recent.id) == null && !back.hasVisited(ann, recent.id), "a removed site is forgotten");
        context.complete();
    }

    /**
     * The whole exchange with two players at the same telescope: each gets his own list, a find lights a guide star
     * for the finder only, and nothing else happens in the world (no entity, no chunk loaded, no Mula appearing).
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aFindOnlyConcernsItsPlayer(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos telescope = context.getAbsolutePos(new BlockPos(1, 1, 1));
        context.setBlockState(new BlockPos(1, 1, 1), ModBlocks.TELESCOPE.getDefaultState());
        ServerPlayerEntity ann = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity bob = context.createMockCreativeServerPlayerInWorld();
        ann.refreshPositionAndAngles(telescope.getX() + 1.5, telescope.getY(), telescope.getZ() + 0.5, 0, 0);
        bob.refreshPositionAndAngles(telescope.getX() - 0.5, telescope.getY(), telescope.getZ() + 0.5, 0, 0);
        MulaSpawnSites sites = MulaSpawnSites.get(world);
        // far away, in chunks nobody loads
        BlockPos farPos = telescope.add(-600, 0, -600), outPos = telescope.add(-4000, 0, 0);
        // The test world is saved from one run to the next, with the sites the ephemerides of the night tests left
        // in it: these two are dated after them all, so whatever the cap retires, it is not them.
        long now = MulaSiteCapGameTests.afterEverySite(world, sites);
        MulaSpawnSites.Site site = sites.add(farPos, now, 4, new int[]{3, 3, 0});
        MulaSpawnSites.Site out = sites.add(outPos, now, 2, new int[]{1});
        context.assertTrue(sites.byId(site.id) == site && sites.byId(out.id) == out, "recorded");
        // By day, whatever the hour the suite reached: the checks below are those of a telescope that is not watched
        long hour = world.getTimeOfDay();
        world.setTimeOfDay(hour - Math.floorMod(hour, 24000L) + 1000);
        try {
            long time = world.getTimeOfDay();
            boolean rain = world.isRaining();
            Box around = new Box(telescope).expand(64);
            int stars = world.getEntitiesByClass(MulaStarEntity.class, around, e -> true).size();
            int mulas = world.getEntitiesByClass(MulaEntity.class, around, e -> true).size();

            List<TelescopePayloads.Night> forAnn = TelescopeService.answer(ann, telescope);
            context.assertTrue(forAnn.stream().anyMatch(n -> n.id() == site.id) && forAnn.stream().noneMatch(n -> n.id() == out.id),
                    "the site in range is listed, not the one beyond");
            TelescopePayloads.Night night = forAnn.stream().filter(n -> n.id() == site.id).findFirst().orElseThrow();
            context.assertTrue(night.day() == 4 && Math.abs(night.dirX() + 0.7071f) < 0.01f && Math.abs(night.dirZ() + 0.7071f) < 0.01f
                            && night.colours().length == 3,
                    "its night, the way its stars went, its colours");

            // Bob was shown nothing yet: he cannot report it; then both look, and only Ann finds it
            context.assertTrue(!TelescopeService.found(bob, site.id), "not found by a player who was not shown that night");
            TelescopeService.answer(bob, telescope);
            context.assertTrue(!TelescopeService.found(ann, out.id), "not a night out of range");
            context.assertTrue(TelescopeService.found(ann, site.id), "found by Ann");
            context.assertTrue(!TelescopeService.found(ann, site.id), "once");
            context.assertTrue(TelescopeService.guides(ann).size() == 1 && TelescopeService.guides(ann).get(0).x() == farPos.getX()
                    && TelescopeService.guides(ann).get(0).z() == farPos.getZ(), "her guide star stands over the site");
            context.assertTrue(TelescopeService.guides(bob).isEmpty(), "Bob has no guide star");
            context.assertTrue(TelescopeService.nights(ann, telescope).stream().noneMatch(n -> n.id() == site.id), "no longer a night to replay for her");
            context.assertTrue(TelescopeService.nights(bob, telescope).stream().anyMatch(n -> n.id() == site.id), "still one for Bob");
            context.assertTrue(TelescopeService.found(bob, site.id) && TelescopeService.guides(bob).size() == 1, "Bob finds it in his turn");

            // Nothing else happened
            context.assertTrue(world.getTimeOfDay() == time && world.isRaining() == rain, "the time and the weather are untouched");
            context.assertTrue(!site.spawned && !out.spawned, "no Mula came early");
            context.assertTrue(!world.getChunkManager().isChunkLoaded(farPos.getX() >> 4, farPos.getZ() >> 4), "the site's chunk is not loaded");
            context.assertTrue(world.getEntitiesByClass(MulaStarEntity.class, around, e -> true).size() == stars
                    && world.getEntitiesByClass(MulaEntity.class, around, e -> true).size() == mulas, "no shooting star, no Mula");

            // Not under a roof
            context.setBlockState(new BlockPos(1, 3, 1), Blocks.STONE);
            context.assertTrue(!TelescopeService.canWatch(world, telescope), "no sky under a roof");
            context.assertTrue(!TelescopeService.use(ann, telescope), "refused");

            // Players standing at the site: their guide star goes out (checked from where they are, nothing loaded)
            sites.visitAround(ann.getUuid(), farPos.add(5, 30, 5), TelescopeService.REACH);
            context.assertTrue(TelescopeService.guides(ann).isEmpty() && TelescopeService.guides(bob).size() == 1, "hers went out, not his");
            TelescopeService.visits(world);
            context.assertTrue(TelescopeService.guides(bob).size() == 1, "standing at the telescope is not being there");
        } finally {
            world.setTimeOfDay(hour);
            sites.remove(site.id);
            sites.remove(out.id);
            context.setBlockState(new BlockPos(1, 3, 1), Blocks.AIR);
            world.getServer().getPlayerManager().remove(ann);
            world.getServer().getPlayerManager().remove(bob);
            TelescopeService.forget(ann.getUuid());
            TelescopeService.forget(bob.getUuid());
        }
        context.complete();
    }

    /**
     * One player at a time at the eyepiece: he is the telescope's watcher (what the clients are told, never saved) and
     * stands where his eye meets it; another is refused until he leaves; going to another telescope frees the first.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oneWatcherAtATimeSeenByAll(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos first = new BlockPos(2, 1, 2), second = new BlockPos(5, 1, 2);
        for (int x = 0; x <= 7; x++) for (int z = 0; z <= 4; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        context.setBlockState(first, ModBlocks.TELESCOPE.getDefaultState());
        context.setBlockState(second, ModBlocks.TELESCOPE.getDefaultState());
        TelescopeBlockEntity telescope = context.getBlockEntity(first), other = context.getBlockEntity(second);
        BlockPos at = context.getAbsolutePos(first), otherAt = context.getAbsolutePos(second);
        ServerPlayerEntity ann = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity bob = context.createMockCreativeServerPlayerInWorld();
        try {
            ann.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() - 2.0, 0, -40);
            bob.refreshPositionAndAngles(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 90, -40);
            context.assertTrue(telescope.getWatcher() == null && !telescope.toInitialChunkDataNbt(world.getRegistryManager()).getBoolean("Watched"),
                    "nobody at first");

            context.assertTrue(TelescopeService.comeToEyepiece(ann, at, telescope), "Ann comes to the eyepiece");
            context.assertTrue(ann.getUuid().equals(telescope.getWatcher()), "she is its watcher");
            double[] neck = new double[3];
            TelescopeMath.neck(0, -40, neck);
            context.assertTrue(Math.abs(ann.getX() - (at.getX() + 0.5 + neck[0])) < 0.01 && Math.abs(ann.getZ() - (at.getZ() + 0.5 + neck[2])) < 0.01,
                    "standing where her eye meets it: " + ann.getPos());
            context.assertTrue(neck[2] < -0.3 && neck[2] > -0.8 && Math.abs(neck[0]) < 1e-9, "just behind the eyepiece: " + neck[2]);

            // What the clients are told, and nothing of it is saved
            NbtCompound sync = telescope.toInitialChunkDataNbt(world.getRegistryManager());
            TelescopeBlockEntity seen = new TelescopeBlockEntity(at, ModBlocks.TELESCOPE.getDefaultState());
            seen.read(sync, world.getRegistryManager());
            context.assertTrue(ann.getUuid().equals(seen.getWatcher()), "the clients know who looks");
            context.assertTrue(!telescope.createNbt(world.getRegistryManager()).containsUuid("Watcher"), "not saved");

            context.assertTrue(!TelescopeService.comeToEyepiece(bob, at, telescope) && ann.getUuid().equals(telescope.getWatcher()),
                    "Bob is refused while she is there");
            context.assertTrue(TelescopeService.comeToEyepiece(ann, at, telescope), "she may click it again");

            // She goes to the other telescope: the first is free
            context.assertTrue(TelescopeService.comeToEyepiece(ann, otherAt, other), "Ann at the other one");
            context.assertTrue(telescope.getWatcher() == null && ann.getUuid().equals(other.getWatcher()), "the first is free");
            context.assertTrue(TelescopeService.comeToEyepiece(bob, at, telescope) && bob.getUuid().equals(telescope.getWatcher()), "Bob takes it");
            TelescopeService.leave(bob);
            context.assertTrue(telescope.getWatcher() == null && TelescopeService.watching(bob.getUuid()) == null, "and leaves it");
            seen.read(telescope.toInitialChunkDataNbt(world.getRegistryManager()), world.getRegistryManager());
            context.assertTrue(seen.getWatcher() == null, "the clients know nobody looks");

            // A watcher who walked away does not keep it
            ann.refreshPositionAndAngles(otherAt.getX() + 0.5, otherAt.getY(), otherAt.getZ() + 9.5, 0, 0);
            context.assertTrue(!TelescopeService.isAt(world, otherAt, ann.getUuid()), "too far to be at it");
            context.assertTrue(TelescopeService.comeToEyepiece(bob, otherAt, other) && bob.getUuid().equals(other.getWatcher()), "Bob takes her place");
        } finally {
            TelescopeService.leave(ann);
            TelescopeService.leave(bob);
            world.getServer().getPlayerManager().remove(ann);
            world.getServer().getPlayerManager().remove(bob);
            TelescopeService.forget(ann.getUuid());
            TelescopeService.forget(bob.getUuid());
        }
        context.complete();
    }

    /** The eyepiece is within a player's reach whatever the aim: he stands, or bends no lower than when sneaking. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theEyepieceIsAtAPlayersHeight(TestContext context) {
        double[] neck = new double[3];
        for (int pitch = -20; pitch >= -75; pitch -= 5) {
            TelescopeMath.neck(37, pitch, neck);
            double height = TelescopeMath.PIVOT_HEIGHT + neck[1];
            context.assertTrue(height <= TelescopeMath.NECK_HEIGHT + 0.06 && height >= TelescopeMath.NECK_HEIGHT - TelescopeMath.BEND_DROP - 0.06,
                    "neck at " + height + " for a tilt of " + pitch);
            double back = Math.sqrt(neck[0] * neck[0] + neck[2] * neck[2]);
            context.assertTrue(pitch < -60 || back > 0.2 && back < 0.8, "behind the pivot: " + back);
        }
        context.assertTrue(TelescopeMath.bend(TelescopeMath.NECK_HEIGHT) == 0 && TelescopeMath.bend(TelescopeMath.NECK_HEIGHT - TelescopeMath.BEND_DROP) == 1
                && TelescopeMath.bend(0) == 1 && TelescopeMath.bend(3) == 0, "the bend");
        context.complete();
    }

    /** The block: its block entity (it is drawn by it), 16 ways to point, turned with the structure it is in. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void telescopeBlockPointsSixteenWays(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.TELESCOPE.getDefaultState().with(TelescopeBlock.ROTATION, 3));
        context.assertTrue(context.getBlockEntity(pos) instanceof TelescopeBlockEntity, "its block entity");
        context.assertTrue(context.getBlockState(pos).rotate(BlockRotation.CLOCKWISE_90).get(TelescopeBlock.ROTATION) == 7, "turned a quarter");
        context.assertTrue(context.getBlockState(pos).rotate(BlockRotation.CLOCKWISE_180).get(TelescopeBlock.ROTATION) == 11, "turned a half");
        context.complete();
    }
}
