package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaSpawnSites;
import fr.lordfinn.steveparty.config.ServerConfig;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.payloads.custom.TelescopePayloads;
import fr.lordfinn.steveparty.telescope.TelescopeService;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The rolling cap of Mula spawn sites: at most {@code mulaMaxSites} per dimension, the oldest retired when one more
 * comes, with its wild Mulas still there (not those made somebody's or taken away), now or when they next load; old
 * saves trimmed the same way; the Telescope never showing a retired site.
 * <p>
 * The tests that change the cap or the world's list do it within one tick (and put the cap back), so that no other
 * test sees it.
 */
public class MulaSiteCapGameTests implements FabricGameTest {

    private static MulaEntity mula(ServerWorld world, BlockPos at) {
        MulaEntity mula = ModEntities.MULA_ENTITY.create(world);
        mula.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        mula.setVariant(MulaEntity.MulaVariant.BLUE);
        return mula;
    }

    /**
     * A time later than every site of the world's list: the test world is saved from one run to the next (and may
     * even come from another world, its sites dated after this one's clock), and a site recorded with an earlier
     * time than those would be the oldest, retired at once.
     */
    static long afterEverySite(ServerWorld world, MulaSpawnSites sites) {
        long time = world.getTime();
        for (MulaSpawnSites.Site s : sites.sites()) time = Math.max(time, s.time + 1);
        return time;
    }

    /** As if read back from a saved chunk: a new entity made from its saved data. */
    private static MulaEntity reloaded(ServerWorld world, MulaEntity mula) {
        NbtCompound nbt = new NbtCompound();
        if (!mula.saveSelfNbt(nbt)) throw new AssertionError("a Mula is saved");
        Entity entity = EntityType.getEntityFromNbt(nbt, world).orElseThrow();
        return (MulaEntity) entity;
    }

    /** 10 sites by default; the 11th retires the oldest (by its time, not by when it was recorded), ids never reused. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theEleventhSiteRetiresTheOldest(TestContext context) {
        ServerConfig config = ServerConfig.get();
        int before = config.mulaMaxSites;
        try {
            context.assertTrue(new ServerConfig().mulaMaxSites == 10, "10 sites by default");
            config.mulaMaxSites = 10;
            MulaSpawnSites sites = new MulaSpawnSites();
            UUID ann = UUID.randomUUID(), bob = UUID.randomUUID();
            List<MulaSpawnSites.Site> added = new ArrayList<>();
            // the second one recorded is the oldest night
            long[] times = {500, 100, 600, 700, 800, 900, 1000, 1100, 1200, 1300};
            for (int i = 0; i < 10; i++) added.add(sites.add(new BlockPos(i * 1000, 64, 0), times[i], times[i] / 24000, new int[]{1, 1, 1}));
            context.assertTrue(sites.siteCount() == 10 && sites.pendingCount() == 10, "ten sites: all kept");
            MulaSpawnSites.Site oldest = added.get(1), first = added.get(0);
            context.assertTrue(sites.markFound(ann, oldest.id) && sites.markFound(bob, first.id), "found");
            sites.visitAround(bob, oldest.pos, 5);
            int epoch = MulaSpawnSites.epoch();

            MulaSpawnSites.Site eleventh = sites.add(new BlockPos(50_000, 64, 0), 2000, 0, new int[]{2});
            context.assertTrue(sites.siteCount() == 10, "still ten");
            context.assertTrue(sites.byId(oldest.id) == null && sites.byId(first.id) == first && sites.byId(eleventh.id) == eleventh,
                    "the oldest night is retired, not the first recorded");
            context.assertTrue(MulaSpawnSites.epoch() != epoch, "the Mulas are told to look");
            context.assertTrue(sites.guides(ann).isEmpty() && !sites.hasFound(ann, oldest.id) && !sites.hasVisited(bob, oldest.id),
                    "what the players knew of it is dropped");
            context.assertTrue(sites.guides(bob).size() == 1 && sites.guides(bob).get(0) == first, "the other guide stars stay");
            context.assertTrue(!sites.markFound(ann, oldest.id) && sites.unfoundNear(ann, oldest.pos, 100, 8).isEmpty(),
                    "a retired site is neither found nor listed");

            // a site older than all the others is the one retired, at once
            MulaSpawnSites.Site stale = sites.add(new BlockPos(60_000, 64, 0), 50, 0, new int[]{3});
            context.assertTrue(sites.siteCount() == 10 && sites.byId(stale.id) == null && sites.byId(first.id) == first, "older than all: retired");
            context.assertTrue(eleventh.id == 11 && stale.id == 12 && sites.add(new BlockPos(70_000, 64, 0), 3000, 0, new int[]{1}).id == 13,
                    "ids are never used twice");
            context.assertTrue(sites.byId(first.id) == null && sites.guides(bob).isEmpty(), "and so on, the oldest first");

            // The config's value is the rule: lowered, the list is trimmed to it; raised, more are kept
            config.mulaMaxSites = 4;
            sites.trim();
            context.assertTrue(MulaSpawnSites.maxSites() == 4 && sites.siteCount() == 4, "trimmed to the config's cap: " + sites.siteCount());
            long earliest = Long.MAX_VALUE;
            for (MulaSpawnSites.Site s : sites.sites()) earliest = Math.min(earliest, s.time);
            context.assertTrue(earliest == 1200, "the four latest are kept: from " + earliest);
            config.mulaMaxSites = 6;
            for (int i = 0; i < 5; i++) sites.add(new BlockPos(80_000 + i * 1000, 64, 0), 4000 + i, 0, new int[]{1});
            context.assertTrue(sites.siteCount() == 6, "six with a cap of six: " + sites.siteCount());
            config.mulaMaxSites = 0;
            context.assertTrue(MulaSpawnSites.maxSites() == 1, "never less than one");
        } finally {
            config.mulaMaxSites = before;
        }
        context.complete();
    }

    /** A save holding more sites than the cap is trimmed when it is read: the oldest first, with what was known of them. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anOldSaveIsTrimmedOnLoad(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerConfig config = ServerConfig.get();
        int before = config.mulaMaxSites;
        try {
            config.mulaMaxSites = 20;
            MulaSpawnSites sites = new MulaSpawnSites();
            UUID ann = UUID.randomUUID();
            List<MulaSpawnSites.Site> added = new ArrayList<>();
            for (int i = 0; i < 14; i++) added.add(sites.add(new BlockPos(i * 1000, 64, 0), 1000 + i, i, new int[]{i % 5}));
            added.get(2).spawned = true;
            added.get(12).spawned = true;
            context.assertTrue(sites.markFound(ann, added.get(1).id) && sites.markFound(ann, added.get(9).id), "found");
            sites.visitAround(ann, added.get(3).pos, 5);
            sites.visitAround(ann, added.get(13).pos, 5);
            NbtCompound saved = sites.writeNbt(new NbtCompound(), world.getRegistryManager());

            config.mulaMaxSites = 10;
            MulaSpawnSites back = MulaSpawnSites.fromNbt(saved, world.getRegistryManager());
            context.assertTrue(back.siteCount() == 10, "trimmed to ten: " + back.siteCount());
            for (int i = 0; i < 14; i++) {
                context.assertTrue((back.byId(added.get(i).id) == null) == (i < 4), "the four oldest are gone, the others kept: " + i);
            }
            context.assertTrue(back.byId(added.get(12).id).spawned && back.pendingCount() == 9, "the sites kept are as they were");
            context.assertTrue(!back.hasFound(ann, added.get(1).id) && !back.hasVisited(ann, added.get(3).id), "nothing is known of the retired ones");
            context.assertTrue(back.hasFound(ann, added.get(9).id) && back.hasVisited(ann, added.get(13).id)
                    && back.guides(ann).size() == 1, "what was known of the others is kept");
            context.assertTrue(back.add(new BlockPos(0, 64, 0), 5000, 0, new int[]{1}).id == 15, "no id of a retired site is used again");

            // Under the cap: read as it is
            config.mulaMaxSites = 20;
            context.assertTrue(MulaSpawnSites.fromNbt(saved, world.getRegistryManager()).siteCount() == 14, "nothing trimmed under the cap");
        } finally {
            config.mulaMaxSites = before;
        }
        context.complete();
    }

    /**
     * A site retired while its Mulas are loaded: the wild ones still there leave with it; the tamed one, the named
     * one and the one taken away stay, and belong to no site any more.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void aRetiredSiteTakesItsWildMulasOnly(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerConfig config = ServerConfig.get();
        int before = config.mulaMaxSites;
        BlockPos here = context.getAbsolutePos(new BlockPos(2, 2, 2));
        MulaSpawnSites sites = MulaSpawnSites.get(world);
        List<MulaSpawnSites.Site> later = new ArrayList<>();
        List<MulaEntity> mine = new ArrayList<>();
        MulaEntity wild, wildToo, tamed, named, away;
        try {
            config.mulaMaxSites = 3;
            long now = afterEverySite(world, sites);
            MulaSpawnSites.Site site = sites.add(here, now, new int[]{0, 1, 2, 3, 4});
            sites.tick(world);
            context.assertTrue(site.spawned, "its Mulas came down");
            for (MulaEntity m : world.getEntitiesByClass(MulaEntity.class, new Box(here).expand(8, 40, 8), m -> m.getSpawnSite() == site.id)) mine.add(m);
            context.assertTrue(mine.size() == 5, "five Mulas of that site: " + mine.size());
            wild = mine.get(0);
            wildToo = mine.get(1);
            tamed = mine.get(2);
            named = mine.get(3);
            away = mine.get(4);
            context.assertTrue(!wild.isKeptFromSiteRetirement(), "a wild Mula at its site is not kept");
            tamed.setTamed(true, false);
            named.setCustomName(Text.literal("Mulette"));
            context.assertTrue(tamed.getSpawnSite() == 0 && named.getSpawnSite() == 0, "made somebody's: it leaves its site at once");
            away.refreshPositionAndAngles(here.getX() + 0.5 + MulaSpawnSites.AWAY + 1, here.getY(), here.getZ() + 0.5, 0, 0);
            context.assertTrue(away.getSpawnSite() == site.id && away.isKeptFromSiteRetirement(), "taken farther than " + MulaSpawnSites.AWAY + " blocks: kept");
            away.refreshPositionAndAngles(here.getX() + 0.5 + MulaSpawnSites.AWAY - 2, here.getY(), here.getZ() + 0.5, 0, 0);
            context.assertTrue(!away.isKeptFromSiteRetirement(), "not when still within it");
            away.refreshPositionAndAngles(here.getX() + 0.5, here.getY(), here.getZ() + 0.5 - MulaSpawnSites.AWAY - 1, 0, 0);

            // Nothing leaves while the site is there
            context.assertTrue(!wild.leaveWithRetiredSite() && !wild.isRemoved() && wild.getSpawnSite() == site.id, "its site is there: it stays");
            for (int i = 1; i <= 3; i++) {
                context.assertTrue(sites.byId(site.id) == site, "not retired before the cap is passed");
                later.add(sites.add(here.add(-30_000 - i * 1000, 0, -30_000), now + i, new int[]{1}));
            }
            context.assertTrue(sites.byId(site.id) == null && sites.siteCount() == 3, "one more than the cap: the oldest is retired");
            context.assertTrue(MulaSpawnSites.isRetired(world.getServer(), world.getRegistryKey().getValue(), site.id)
                    && !MulaSpawnSites.isRetired(world.getServer(), world.getRegistryKey().getValue(), later.get(0).id), "retired, and only it");

            context.assertTrue(wild.leaveWithRetiredSite() && wild.isRemoved(), "the wild Mula at its site goes with it");
            context.assertTrue(!away.leaveWithRetiredSite() && !away.isRemoved() && away.getSpawnSite() == 0, "the one taken away stays, of no site");
            context.assertTrue(!tamed.leaveWithRetiredSite() && !tamed.isRemoved() && !named.leaveWithRetiredSite() && !named.isRemoved(),
                    "the tamed and the named stay");
        } catch (Throwable t) {
            mine.forEach(Entity::discard);
            for (MulaSpawnSites.Site s : later) sites.retire(s.id);
            throw t;
        } finally {
            config.mulaMaxSites = before;
        }
        // The other wild one is not told anything: it sees its site gone by itself, on its next tick
        MulaEntity left = wildToo;
        context.runAtTick(5, () -> {
            try {
                context.assertTrue(left.isRemoved(), "the other wild Mula left on its own");
                context.assertTrue(!tamed.isRemoved() && !named.isRemoved() && !away.isRemoved(), "the kept ones are still there");
            } finally {
                mine.forEach(Entity::discard);
                for (MulaSpawnSites.Site s : later) sites.retire(s.id);
            }
            context.complete();
        });
    }

    /**
     * Mulas that were in unloaded chunks when their site was retired: each decides when it loads, by the same rule
     * (the wild one at its site leaves, the tamed one and the one found far from its site stay), nothing being loaded
     * for it.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void unloadedMulasDecideWhenTheyLoad(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos here = context.getAbsolutePos(new BlockPos(2, 2, 2));
        MulaSpawnSites sites = MulaSpawnSites.get(world);
        long now = afterEverySite(world, sites);
        MulaSpawnSites.Site gone = sites.add(here, now, new int[]{1});
        gone.spawned = true;
        MulaSpawnSites.Site alive = sites.add(here.add(-40_000, 0, -40_000), now + 1, new int[]{1});
        alive.spawned = true;
        sites.retire(gone.id);
        context.assertTrue(sites.byId(gone.id) == null && sites.byId(alive.id) == alive, "one retired, one alive");
        // a Mula of a living site is untouched, until that site is retired in its turn (all within this tick: the
        // world's list is shared with the other tests)
        MulaEntity ofAlive = mula(world, here.up(3));
        ofAlive.setSpawnSite(world.getRegistryKey(), alive.id, here.getX(), here.getZ());
        context.assertTrue(!ofAlive.leaveWithRetiredSite() && !ofAlive.isRemoved() && ofAlive.getSpawnSite() == alive.id, "its site lives: untouched");
        sites.retire(alive.id);
        context.assertTrue(ofAlive.leaveWithRetiredSite() && ofAlive.isRemoved(), "the wild one of the site retired now leaves");

        // What the chunks held: saved before the retirement, read after it
        MulaEntity wild = mula(world, here);
        wild.setSpawnSite(world.getRegistryKey(), gone.id, here.getX(), here.getZ());
        MulaEntity tamed = mula(world, here.up());
        tamed.setOwnerUuid(UUID.randomUUID());
        tamed.setTamed(true, false);
        tamed.setSpawnSite(world.getRegistryKey(), gone.id, here.getX(), here.getZ());
        MulaEntity away = mula(world, here.up(2));
        away.setSpawnSite(world.getRegistryKey(), gone.id, here.getX() + 200, here.getZ());
        MulaEntity ofNone = mula(world, here.up(4));
        List<MulaEntity> loaded = new ArrayList<>();
        for (MulaEntity m : List.of(wild, tamed, away, ofNone)) loaded.add(reloaded(world, m));
        context.assertTrue(loaded.get(0).getSpawnSite() == gone.id && loaded.get(1).getSpawnSite() == gone.id && loaded.get(1).isTamed()
                && loaded.get(2).getSpawnSite() == gone.id && loaded.get(3).getSpawnSite() == 0, "their site is saved with them");
        for (MulaEntity m : loaded) context.assertTrue(world.spawnEntity(m), "loaded");

        context.runAtTick(5, () -> {
            try {
                context.assertTrue(loaded.get(0).isRemoved(), "the wild one at its retired site left when it loaded");
                context.assertTrue(!loaded.get(1).isRemoved() && loaded.get(1).getSpawnSite() == 0, "the tamed one stays, of no site");
                context.assertTrue(!loaded.get(2).isRemoved() && loaded.get(2).getSpawnSite() == 0, "the one far from its site stays, of no site");
                context.assertTrue(!loaded.get(3).isRemoved(), "a Mula of no site is untouched");
                // and they are not looked at again: whatever is retired later does not concern the kept ones
                context.assertTrue(!loaded.get(1).leaveWithRetiredSite() && !loaded.get(2).leaveWithRetiredSite()
                        && !loaded.get(1).isRemoved() && !loaded.get(2).isRemoved(), "kept for good");
            } finally {
                loaded.forEach(Entity::discard);
            }
            context.complete();
        });
    }

    /**
     * The Telescope follows: a retired site is no longer a night to replay nor a guide star, for a player who was
     * shown it, who had found it, or who is looking through a telescope when it happens.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theTelescopeForgetsARetiredSite(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerConfig config = ServerConfig.get();
        int before = config.mulaMaxSites;
        BlockPos telescope = context.getAbsolutePos(new BlockPos(1, 1, 1));
        ServerPlayerEntity ann = TestPlayers.mock(context);
        ann.refreshPositionAndAngles(telescope.getX() + 1.5, telescope.getY(), telescope.getZ() + 0.5, 0, 0);
        MulaSpawnSites sites = MulaSpawnSites.get(world);
        List<MulaSpawnSites.Site> later = new ArrayList<>();
        try {
            config.mulaMaxSites = 3;
            long now = afterEverySite(world, sites);
            MulaSpawnSites.Site night = sites.add(telescope.add(-500, 0, -300), now, 3, new int[]{1, 1, 1});
            MulaSpawnSites.Site guide = sites.add(telescope.add(-300, 0, -500), now, 3, new int[]{2, 2, 2});
            List<TelescopePayloads.Night> shown = TelescopeService.answer(ann, telescope);
            context.assertTrue(shown.stream().anyMatch(n -> n.id() == night.id) && shown.stream().anyMatch(n -> n.id() == guide.id), "both nights are shown");
            context.assertTrue(TelescopeService.found(ann, guide.id) && TelescopeService.guides(ann).size() == 1, "one found: her guide star");

            for (int i = 1; i <= 3; i++) later.add(sites.add(telescope.add(-600 - i * 20, 0, -600), now + i, 4, new int[]{3}));
            context.assertTrue(sites.byId(night.id) == null && sites.byId(guide.id) == null, "both are retired");
            context.assertTrue(TelescopeService.guides(ann).isEmpty(), "no guide star leads to a retired site");
            List<TelescopePayloads.Night> now3 = TelescopeService.nights(ann, telescope);
            context.assertTrue(now3.size() == 3 && now3.stream().noneMatch(n -> n.id() == night.id || n.id() == guide.id),
                    "the telescope shows the living sites only: " + now3.size());
            context.assertTrue(!TelescopeService.found(ann, night.id), "a retired night cannot be found, though it was shown");
            // a night shown since is found as usual
            TelescopeService.answer(ann, telescope);
            context.assertTrue(TelescopeService.found(ann, later.get(2).id) && TelescopeService.guides(ann).size() == 1
                    && TelescopeService.guides(ann).get(0).id() == later.get(2).id, "the living ones still work");
        } finally {
            config.mulaMaxSites = before;
            for (MulaSpawnSites.Site s : later) sites.retire(s.id);
            world.getServer().getPlayerManager().remove(ann);
            TelescopeService.forget(ann.getUuid());
        }
        context.complete();
    }
}
