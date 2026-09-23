package fr.lordfinn.steveparty.gametest;

import com.mojang.serialization.DataResult;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.components.InventoryComponent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoreGameTests implements FabricGameTest {

    /** A task scheduling another task from its callback used to crash the server (ConcurrentModificationException). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void schedulerAcceptsTasksScheduledFromTasks(TestContext context) {
        AtomicBoolean first = new AtomicBoolean();
        AtomicBoolean second = new AtomicBoolean();
        Steveparty.SCHEDULER.schedule(UUID.randomUUID(), 1, () -> {
            first.set(true);
            Steveparty.SCHEDULER.schedule(UUID.randomUUID(), 1, () -> second.set(true));
        });
        context.waitAndRun(5, () -> {
            context.assertTrue(first.get(), "first task ran");
            context.assertTrue(second.get(), "task scheduled from a task ran");
            context.complete();
        });
    }

    /** Saving/loading a regular mob must not touch its NoAI / Invulnerable flags (token mixin regression). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nonTokenMobsKeepTheirFlagsThroughSaveAndLoad(TestContext context) {
        PigEntity pig = context.spawnMob(EntityType.PIG, new BlockPos(1, 1, 1));
        pig.setAiDisabled(true);
        pig.setInvulnerable(true);
        pig.setCustomNameVisible(true);
        NbtCompound nbt = new NbtCompound();
        pig.writeNbt(nbt);
        context.assertTrue(!nbt.contains("Tokenized"), "no token data written for a regular mob");

        PigEntity reloaded = EntityType.PIG.create(context.getWorld(), SpawnReason.LOAD);
        context.assertTrue(reloaded != null, "pig created");
        reloaded.readNbt(nbt);
        context.assertTrue(reloaded.isAiDisabled(), "NoAI kept");
        context.assertTrue(reloaded.isInvulnerable(), "Invulnerable kept");
        context.assertTrue(reloaded.isCustomNameVisible(), "CustomNameVisible kept");
        context.complete();
    }

    /** Ghost quantities above 99 (cartridge slots) must survive serialization. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inventoryComponentKeepsBigCounts(TestContext context) {
        InventoryComponent component = new InventoryComponent(List.of(new ItemStack(Items.DIAMOND, 500), ItemStack.EMPTY));
        RegistryOps<NbtElement> ops = context.getWorld().getRegistryManager().getOps(NbtOps.INSTANCE);
        DataResult<NbtElement> encoded = InventoryComponent.CODEC.encodeStart(ops, component);
        context.assertTrue(encoded.isSuccess(), "encodes: " + encoded);
        InventoryComponent decoded = InventoryComponent.CODEC.parse(ops, encoded.getOrThrow()).getOrThrow();
        context.assertEquals(decoded.getStack(0).getCount(), 500, "count");
        context.assertTrue(decoded.getStack(1).isEmpty(), "empty slot kept");
        context.assertEquals(decoded, component, "value equality");
        context.complete();
    }

    /** Starting a party with no start tile around must not crash nor leave a broken party. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bootingEmptyPartyDoesNotCrash(TestContext context) {
        BlockPos controller = new BlockPos(3, 1, 3);
        context.setBlockState(controller.down(), Blocks.STONE);
        context.setBlockState(controller, ModBlocks.PARTY_CONTROLLER);
        context.setBlockState(controller.east(), Blocks.REDSTONE_BLOCK);
        context.waitAndRun(40, context::complete);
    }
}
