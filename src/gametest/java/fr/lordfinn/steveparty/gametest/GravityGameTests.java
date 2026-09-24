package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GravityCoreBlockEntity;
import fr.lordfinn.steveparty.entities.custom.ForgeCoreEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.utils.GravityPull;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.*;

public class GravityGameTests implements FabricGameTest {
    private static final BlockPos FORGE_POS = new BlockPos(1, 1, 1);
    /** Core insertion, then the core rising to its highest (4 black fragments: 16 blocks). */
    private static final int RISE_TICKS = CORE_INSERT_TICKS + 160;

    /** An activated forge with 4 black fragments: its core rises to the top. */
    private static DiceForgeBlockEntity risingForge(TestContext context) {
        context.setBlockState(FORGE_POS, ModBlocks.DICE_FORGE.getDefaultState());
        DiceForgeBlockEntity forge = context.getBlockEntity(FORGE_POS);
        forge.setStack(CENTER_SLOT, new ItemStack(ModBlocks.GRAVITY_CORE));
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            forge.setStack(FIRST_FRAGMENT_SLOT + i, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        }
        return forge;
    }

    private static ArmorStandEntity standAt(TestContext context, Vec3d absolute) {
        ArmorStandEntity stand = new ArmorStandEntity(context.getWorld(), absolute.x, absolute.y, absolute.z);
        context.getWorld().spawnEntity(stand);
        return stand;
    }

    private static void wearNetherite(ArmorStandEntity stand) {
        stand.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        stand.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        stand.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        stand.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
    }

    /** Armour weighs an entity down: none is fully pulled, full netherite not at all, diamond a little. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void armourResistsThePull(TestContext context) {
        ArmorStandEntity bare = standAt(context, context.getAbsolute(new Vec3d(1.5, 2, 1.5)));
        ArmorStandEntity netherite = standAt(context, context.getAbsolute(new Vec3d(2.5, 2, 1.5)));
        wearNetherite(netherite);
        ArmorStandEntity diamond = standAt(context, context.getAbsolute(new Vec3d(3.5, 2, 1.5)));
        diamond.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        diamond.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        diamond.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        diamond.equipStack(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        context.waitAndRun(2, () -> {
            context.assertTrue(GravityPull.resistance(bare) == 0, "no armour: fully pulled");
            context.assertTrue(GravityPull.resistance(netherite) >= 1, "full netherite: not pulled");
            double d = GravityPull.resistance(diamond);
            context.assertTrue(d > 0.8 && d < 1, "full diamond: barely pulled (" + d + ")");
            // The bigger the hitbox, the less pulled
            var golem = EntityType.IRON_GOLEM.create(context.getWorld(), net.minecraft.entity.SpawnReason.COMMAND);
            var cow = EntityType.COW.create(context.getWorld(), net.minecraft.entity.SpawnReason.COMMAND);
            context.assertTrue(GravityPull.resistance(cow) > 0.3, "a cow is bigger than a player: less pulled");
            context.assertTrue(GravityPull.resistance(golem) > GravityPull.resistance(cow), "an iron golem even less");
            context.complete();
        });
    }

    /** The risen core draws what floats near it; full netherite is not moved (it just falls). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = RISE_TICKS + 100, batchId = "gravity_forge_pull")
    public void theRisenCorePullsWhatIsAroundIt(TestContext context) {
        DiceForgeBlockEntity forge = risingForge(context);
        context.waitAndRun(RISE_TICKS, () -> {
            Vec3d core = forge.getCoreCenter();
            context.assertTrue(core.y - context.getAbsolutePos(FORGE_POS).getY() > MAX_CORE_ALTITUDE - 1, "core at the top: " + core);
            // Both inside the test area (its sides have walls)
            ArmorStandEntity pulled = standAt(context, core.add(5, -1, 0));
            ArmorStandEntity heavy = standAt(context, core.add(0, -1, 5));
            wearNetherite(heavy);
            double heavyX = heavy.getX();
            context.waitAndRun(30, () -> {
                double distance = pulled.getBoundingBox().getCenter().distanceTo(core);
                context.assertTrue(Math.abs(distance - PULL_ORBIT) < 1, "drawn to its orbit around the core: " + distance);
                context.assertTrue(Math.abs(heavy.getX() - heavyX) < 0.01, "netherite: not drawn sideways");
                context.assertTrue(heavy.getY() < core.y - 2, "netherite: falls");
                context.complete();
            });
        });
    }

    /** Hitting the core in the sky blows it up: the forge loses its core and everything near is flung away. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = RISE_TICKS + 100, batchId = "gravity_forge_blast")
    public void hittingTheCoreBlowsItUp(TestContext context) {
        DiceForgeBlockEntity forge = risingForge(context);
        context.waitAndRun(RISE_TICKS, () -> {
            Vec3d core = forge.getCoreCenter();
            List<ForgeCoreEntity> hitboxes = context.getWorld().getEntitiesByClass(ForgeCoreEntity.class,
                    new Box(core, core).expand(1), e -> true);
            context.assertTrue(hitboxes.size() == 1, "one hitbox on the core: " + hitboxes.size());
            CowEntity cow = EntityType.COW.create(context.getWorld(), net.minecraft.entity.SpawnReason.COMMAND);
            cow.refreshPositionAndAngles(core.x + 3, core.y - 0.5, core.z, 0, 0);
            context.getWorld().spawnEntity(cow);
            hitboxes.get(0).damage(context.getWorld(), context.getWorld().getDamageSources().generic(), 1f);
            context.assertTrue(!forge.isActivated(), "the core is gone");
            context.assertTrue(forge.getStack(FIRST_FRAGMENT_SLOT).isOf(ModItems.BLACK_STAR_FRAGMENT), "nothing else lost");
            context.waitAndRun(3, () -> {
                double distance = cow.getPos().distanceTo(core);
                context.assertTrue(distance > 6, "flung away: " + distance);
                context.complete();
            });
        });
    }

    /** A placed gravity core pulls just the same, with a fixed reach and strength, and what it pulls orbits it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "gravity_block")
    public void theGravityCoreBlockPullsToo(TestContext context) {
        BlockPos corePos = new BlockPos(1, 10, 1); // high up: room all around (the test area has walls on its sides)
        context.setBlockState(corePos, ModBlocks.GRAVITY_CORE.getDefaultState());
        Vec3d center = Vec3d.of(context.getAbsolutePos(corePos)).add(0.5, 0.75, 0.5);
        ArmorStandEntity stand = standAt(context, center.add(4, 0, 0));
        context.waitAndRun(40, () -> {
            Vec3d at = stand.getBoundingBox().getCenter().subtract(center);
            double distance = at.length();
            context.assertTrue(Math.abs(distance - GravityCoreBlockEntity.ORBIT) < 0.8, "on its orbit around the block: " + distance);
            double angle = Math.atan2(at.z, at.x);
            context.waitAndRun(10, () -> {
                Vec3d later = stand.getBoundingBox().getCenter().subtract(center);
                double turned = Math.abs(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(later.z, later.x) - angle)));
                context.assertTrue(turned > 15, "circling it: turned " + turned + " degrees");
                context.complete();
            });
        });
    }
}
