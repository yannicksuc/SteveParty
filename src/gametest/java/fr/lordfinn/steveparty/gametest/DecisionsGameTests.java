package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

import java.util.UUID;

public class DecisionsGameTests implements FabricGameTest {

    /** Like a wolf, a sitting Mula stands up when hurt. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sittingMulaStandsUpWhenHit(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        mula.setSitting(true);
        mula.damage(context.getWorld(), context.getWorld().getDamageSources().generic(), 1.0F);
        context.assertTrue(!mula.isSitting(), "stands up when hurt");
        context.complete();
    }

    /** A dead trader releases its shop: owner and links are forgotten. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void deadTraderForgetsOwnerAndLinks(TestContext context) {
        HidingTraderEntity trader = context.spawnEntity(ModEntities.HIDING_TRADER_ENTITY, new BlockPos(1, 2, 1));
        VendorLinkPersistentState links = VendorLinkPersistentState.get(context.getWorld().getServer());
        GlobalPos stall = GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(new BlockPos(3, 2, 3)));
        links.setOwner(trader.getUuid(), UUID.randomUUID());
        links.linkBlock(trader.getUuid(), stall);

        trader.kill(context.getWorld());

        context.assertTrue(links.getOwner(trader.getUuid()) == null, "owner forgotten");
        context.assertTrue(links.getVendorsLinkedTo(stall).isEmpty(), "links forgotten");
        context.complete();
    }
}
