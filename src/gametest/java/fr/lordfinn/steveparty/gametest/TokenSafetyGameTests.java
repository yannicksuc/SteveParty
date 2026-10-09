package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.effect.ModEffects;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenBase;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;

/**
 * A token stays a pawn whatever happens around it: a creeper token never explodes (nor leaves the spell in its cloud
 * when a creeper does), lightning leaves tokens as they are, the vanilla actions that would turn it into something
 * else do nothing (a horse token can still be ridden), and its memories (a villager's job site) are kept.
 */
public class TokenSafetyGameTests implements FabricGameTest {
    private static void floor(TestContext context) {
        for (int x = 0; x < 8; x++)
            for (int z = 0; z < 8; z++)
                context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE.getDefaultState());
    }

    private static <T extends MobEntity> T token(T mob) {
        ((TokenizedEntityInterface) mob).steveparty$setTokenized(true);
        return mob;
    }

    private static void disconnect(TestContext context, ServerPlayerEntity player) {
        TestPlayers.remove(context, player);
    }

    /** Lit before the spell (already hissing) or after: a creeper token never explodes. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
    public void creeperTokenNeverExplodes(TestContext context) {
        floor(context);
        CreeperEntity hissing = context.spawnEntity(EntityType.CREEPER, new BlockPos(2, 2, 2));
        hissing.setFuseSpeed(1);
        token(hissing);
        CreeperEntity lit = token(context.spawnEntity(EntityType.CREEPER, new BlockPos(5, 2, 5)));
        lit.ignite();
        context.waitAndRun(80, () -> {
            context.assertTrue(hissing.isAlive() && !hissing.isRemoved(), "a token tokenized while hissing did not explode");
            context.assertTrue(lit.isAlive() && !lit.isRemoved(), "a lit token did not explode");
            context.assertTrue(hissing.getFuseSpeed() <= 0 && !lit.isIgnited(), "their fuses went out");
            context.complete();
        });
    }

    /** The token spell is never released in the cloud of an exploding creeper: with only it, no cloud at all. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
    public void creeperCloudHasNoTokenSpell(TestContext context) {
        floor(context);
        CreeperEntity creeper = context.spawnEntity(EntityType.CREEPER, new BlockPos(4, 2, 4));
        creeper.addStatusEffect(new StatusEffectInstance(ModEffects.SQUISHED, 2000, 5));
        creeper.ignite();
        context.waitAndRun(60, () -> {
            context.assertTrue(creeper.isRemoved(), "the creeper exploded");
            Box around = new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(12);
            context.assertTrue(context.getWorld().getEntitiesByClass(AreaEffectCloudEntity.class, around, cloud -> true).isEmpty(),
                    "no cloud carrying the token spell");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void lightningLeavesTokensAsTheyAre(TestContext context) {
        floor(context);
        CreeperEntity creeper = token(context.spawnEntity(EntityType.CREEPER, new BlockPos(2, 2, 2)));
        PigEntity pig = token(context.spawnEntity(EntityType.PIG, new BlockPos(4, 2, 2)));
        VillagerEntity villager = token(context.spawnEntity(EntityType.VILLAGER, new BlockPos(6, 2, 2)));
        for (MobEntity mob : new MobEntity[]{creeper, pig, villager}) {
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(context.getWorld());
            context.assertTrue(bolt != null, "lightning created");
            mob.onStruckByLightning(context.getWorld(), bolt);
            String what = mob.getType().getUntranslatedName();
            context.assertTrue(!mob.isRemoved() && TokenBase.isToken(mob), what + " token is still the same token");
            context.assertTrue(mob.getFireTicks() <= 0, what + " token is not set on fire");
        }
        context.assertFalse(creeper.writeNbt(new NbtCompound()).getBoolean("powered"), "a creeper token is not charged");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void vanillaActionsDoNotBreakPawns(TestContext context) {
        floor(context);
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            AxolotlEntity axolotl = token(context.spawnEntity(EntityType.AXOLOTL, new BlockPos(1, 2, 1)));
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            context.assertTrue(axolotl.interact(player, Hand.MAIN_HAND) == ActionResult.PASS && !axolotl.isRemoved(),
                    "an axolotl token is not picked up in a bucket");

            MooshroomEntity mooshroom = token(context.spawnEntity(EntityType.MOOSHROOM, new BlockPos(3, 2, 1)));
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
            mooshroom.interact(player, Hand.MAIN_HAND);
            context.assertTrue(!mooshroom.isRemoved(), "a mooshroom token is not sheared into a cow");

            WolfEntity wolf = token(context.spawnEntity(EntityType.WOLF, new BlockPos(5, 2, 1)));
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BONE, 64));
            for (int i = 0; i < 10; i++) wolf.interact(player, Hand.MAIN_HAND);
            context.assertFalse(wolf.isTamed(), "a wolf token is not tamed");

            CreeperEntity creeper = token(context.spawnEntity(EntityType.CREEPER, new BlockPos(1, 2, 4)));
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
            creeper.interact(player, Hand.MAIN_HAND);
            context.assertFalse(creeper.isIgnited(), "flint and steel does not light a creeper token");

            HorseEntity horse = token(context.spawnEntity(EntityType.HORSE, new BlockPos(4, 2, 4)));
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
            horse.interact(player, Hand.MAIN_HAND);
            context.assertFalse(player.hasVehicle() || horse.isTame(), "a horse token is neither fed nor tamed with food");
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            horse.interact(player, Hand.MAIN_HAND);
            context.assertTrue(player.getVehicle() == horse, "a player can ride a tiny horse token, with an empty hand");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    /** Its brain stops, but keeps its memories: a villager token still knows its job site (and so its trades). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void villagerTokenKeepsItsJobSite(TestContext context) {
        floor(context);
        VillagerEntity villager = context.spawnEntity(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        GlobalPos site = GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(new BlockPos(4, 2, 4)));
        villager.getBrain().remember(MemoryModuleType.JOB_SITE, site);
        token(villager);
        context.assertTrue(villager.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE).filter(site::equals).isPresent(),
                "the villager token remembers its job site");
        context.complete();
    }

    /** A Boxed Trader turned into a token is stored in an empty Token like any other token. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void boxedTraderTokenIsStoredInAToken(TestContext context) {
        floor(context);
        BoxedTraderEntity trader = token(context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(3, 2, 3)));
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            ItemStack stack = new ItemStack(ModItems.TOKEN);
            player.setStackInHand(Hand.MAIN_HAND, stack);
            ActionResult result = stack.getItem().useOnEntity(stack, player, trader, Hand.MAIN_HAND);
            context.assertTrue(result == ActionResult.SUCCESS, "the Token takes the trader token: " + result);
            context.assertTrue(trader.isRemoved(), "the trader token left the board");
            context.assertTrue(player.getMainHandStack().get(ModComponents.ENTITY_DATA_COMPONENT) != null, "the Token holds it");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    /** Two Mulas following the same player, resting right on top of each other, drift apart. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 140)
    public void followingMulasKeepApart(TestContext context) {
        ServerPlayerEntity owner = TestPlayers.mock(context);
        BlockPos at = context.getAbsolutePos(new BlockPos(3, 2, 3));
        owner.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        MulaEntity first = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(4, 4, 3));
        MulaEntity second = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(4, 4, 3));
        second.setPosition(first.getPos());
        first.setOwner(owner);
        second.setOwner(owner);
        first.getMulaBrain().refreshNow();
        second.getMulaBrain().refreshNow();
        context.waitAndRun(100, () -> {
            try {
                double distance = first.distanceTo(second);
                context.assertTrue(distance > 1.0, "the two Mulas moved apart: " + distance + " blocks");
            } finally {
                disconnect(context, owner);
            }
            context.complete();
        });
    }
}
