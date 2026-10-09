package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.BoardRuleCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.KeyGateCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ThresholdCartridgeItem;
import fr.lordfinn.steveparty.powerups.effects.TrapEffect;
import fr.lordfinn.steveparty.service.TileInfos;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The game info of the board spaces (TileInfos): what each role tells, computed from the space's state and following
 * its changes, a Trap set on a space, the codec, and where a token is heading (the end of its way).
 */
public class TileInfoGameTests implements FabricGameTest {
    private static final String KEY = "hud.steveparty.tile_info.";

    /** A tile at (1, 1, 1) on stone holding {@code cartridge}. */
    private static BoardSpaceBlockEntity tile(TestContext context, ItemStack cartridge) {
        return tile(context, new BlockPos(1, 1, 1), cartridge);
    }

    private static BoardSpaceBlockEntity tile(TestContext context, BlockPos pos, ItemStack cartridge) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        BoardSpaceBlockEntity space = context.getBlockEntity(pos);
        space.setStack(0, cartridge);
        return space;
    }

    /** The line written with {@code key}, or null. */
    private static @Nullable TranslatableTextContent line(TileInfo info, String key) {
        for (TileInfo.Line line : info.lines()) {
            if (line.text().getContent() instanceof TranslatableTextContent content && content.getKey().equals(KEY + key)) return content;
        }
        return null;
    }

    private static String arg(TileInfo info, String key, int index) {
        TranslatableTextContent content = line(info, key);
        if (content == null || content.getArgs().length <= index) return null;
        Object arg = content.getArgs()[index];
        return arg instanceof Text text ? text.getString() : String.valueOf(arg);
    }

    /** A plain tile tells nothing: no panel. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aPlainTileTellsNothing(TestContext context) {
        BoardSpaceBlockEntity space = tile(context, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        context.assertTrue(TileInfos.of(space).isEmpty(), "a plain tile has no info");
        context.complete();
    }

    /** The Threshold tells its condition, titled by its cartridge; a new condition changes it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theThresholdTellsItsCondition(TestContext context) {
        BoardSpaceBlockEntity space = tile(context, ThresholdCartridgeItem.with(ModItems.THRESHOLD_CARTRIDGE, ThresholdCartridgeItem.Operator.AT_LEAST, 7));
        TileInfo info = TileInfos.of(space);
        context.assertTrue(line(info, "threshold") != null, "the condition line");
        context.assertEquals(info.title().getString(), space.getActiveCartridgeItemStack().getName().getString(), "titled by its cartridge");
        space.setStack(0, ThresholdCartridgeItem.with(ModItems.THRESHOLD_CARTRIDGE, ThresholdCartridgeItem.Operator.AT_LEAST, 9));
        context.assertTrue(!TileInfos.of(space).sameAs(info), "a new condition: a new info");
        context.assertTrue(TileInfos.of(space).sameAs(TileInfos.of(space)), "unchanged: the same info");
        context.complete();
    }

    /** The Common pot tells its coins and its stake, and circles the items stolen into it; it follows the pot. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void thePotTellsWhatItHolds(TestContext context) {
        BoardSpaceBlockEntity space = tile(context, new ItemStack(ModItems.POT_CARTRIDGE));
        ItemStack pot = space.getActiveCartridgeItemStack();
        PotCartridgeItem.setCoins(pot, 7);
        TileInfo before = TileInfos.of(space);
        context.assertEquals(arg(before, "pot", 0), "7", "7 coins in the pot");
        context.assertTrue(line(before, "pot.stake") != null, "its stake");
        context.assertTrue(before.ring().isEmpty(), "nothing stolen yet");
        PotCartridgeItem.setCoins(pot, 12);
        PotCartridgeItem.setItems(pot, List.of(new ItemStack(Items.DIAMOND, 2)));
        TileInfo after = TileInfos.of(space);
        context.assertEquals(arg(after, "pot", 0), "12", "12 coins now");
        context.assertTrue(after.ring().size() == 1 && after.ring().getFirst().isOf(Items.DIAMOND), "the diamonds circle it");
        context.assertTrue(!after.sameAs(before), "changed");
        context.complete();
    }

    /** An item tile circles what it gives and takes, and tells how many of what it gives its chest still holds. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anItemTileTellsItsStock(TestContext context) {
        BlockPos chestPos = new BlockPos(3, 1, 1);
        context.setBlockState(chestPos.down(), Blocks.STONE);
        context.setBlockState(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = context.getBlockEntity(chestPos);
        chest.setStack(0, new ItemStack(Items.DIAMOND, 5));
        ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        ItemStack taken = new ItemStack(Items.DIRT, 2);
        taken.set(ModComponents.IS_NEGATIVE, true);
        cartridge.set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(new ItemStack(Items.DIAMOND, 3), taken)));
        CartridgeContainers.set(cartridge, List.of(GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(chestPos))));
        BoardSpaceBlockEntity space = tile(context, cartridge);
        TileInfo info = TileInfos.of(space);
        context.assertEquals(info.ring().size(), 2, "both items circle it");
        context.assertTrue(Boolean.TRUE.equals(info.ring().get(1).get(ModComponents.IS_NEGATIVE)), "the dirt as taken");
        context.assertTrue(line(info, "inventory.random") != null, "one at random (its default)");
        context.assertEquals(arg(info, "inventory.left", 0), "5", "5 diamonds left");
        chest.setStack(0, new ItemStack(Items.DIAMOND, 1));
        context.assertEquals(arg(TileInfos.of(space), "inventory.left", 0), "1", "1 left once taken");
        context.complete();
    }

    /** The Key gate tells whether it is open, and the ways it locks while closed. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theKeyGateTellsItsState(TestContext context) {
        BoardSpaceBlockEntity space = tile(context, new ItemStack(ModItems.KEY_GATE_CARTRIDGE));
        ItemStack gate = space.getActiveCartridgeItemStack();
        TileInfo closed = TileInfos.of(space);
        TranslatableTextContent state = line(closed, "key_gate");
        context.assertTrue(state != null && ((Text) state.getArgs()[0]).getContent() instanceof TranslatableTextContent value
                && value.getKey().equals(KEY + "key_gate.closed"), "closed");
        BoardRuleCartridgeItem.putState(gate, KeyGateCartridgeItem.OPENED, -1);
        TileInfo open = TileInfos.of(space);
        state = line(open, "key_gate");
        context.assertTrue(state != null && ((Text) state.getArgs()[0]).getContent() instanceof TranslatableTextContent value
                && value.getKey().equals(KEY + "key_gate.open"), "open once a key opened it");
        context.assertTrue(line(open, "key_gate.locks") == null, "open: no way locked");
        context.complete();
    }

    /** The other roles with something to tell: titled by their cartridge, at least a line each. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyRoleWithSettingsTellsThem(TestContext context) {
        List<Item> cartridges = List.of(ModItems.MISTIGRI_CARTRIDGE, ModItems.FROUSSEUX_CARTRIDGE, ModItems.STAR_CARTRIDGE,
                ModItems.SHOP_CARTRIDGE, ModItems.TELEPORT_CARTRIDGE, ModItems.ADVANCE_BACK_CARTRIDGE);
        for (Item item : cartridges) {
            BoardSpaceBlockEntity space = tile(context, new ItemStack(item));
            TileInfo info = TileInfos.of(space);
            context.assertTrue(!info.lines().isEmpty(), item + " tells something");
            context.assertEquals(info.title().getString(), new ItemStack(item).getName().getString(), item + " titled by its cartridge");
        }
        context.complete();
    }

    /** A Trap set on a space: what it does is told (a plain tile then has a panel too). */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tile_info_trap")
    public void aTrapIsTold(TestContext context) {
        TestBoards.floor(context, 6, 6, 0);
        BlockPos t0 = new BlockPos(1, 1, 1), t1 = new BlockPos(3, 1, 1);
        ItemStack first = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
        first.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(t1))), ""));
        tile(context, t0, first);
        BoardSpaceBlockEntity trapped = tile(context, t1, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        ServerPlayerEntity player = TestPlayers.mock(context);
        PigEntity token = TestBoards.token(context, t0, player.getUuid());
        try {
            PartyControllerEntity party = TestBoards.party(context, new BlockPos(1, 1, 4), 2, token.getUuid());
            context.assertTrue(TileInfos.of(trapped).isEmpty(), "nothing before the trap");
            TrapEffect.place(party, context.getAbsolutePos(t1), player.getUuid(), token.getUuid());
            TileInfo info = TileInfos.of(trapped);
            context.assertTrue(info.title().getContent() instanceof TranslatableTextContent title && title.getKey().equals(KEY + "trap.title"),
                    "a plain space with a trap: titled Trap");
            context.assertTrue(!info.lines().isEmpty() && line(info, "trap") == null, "its effect alone (the title says Trap)");
            TrapEffect.clearAll(party);
            context.assertTrue(TileInfos.of(trapped).isEmpty(), "nothing once the trap is gone");
            context.removeBlock(new BlockPos(1, 1, 4));
        } finally {
            ((TokenizedEntityInterface) token).steveparty$setTokenized(false);
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /** The info goes over the network as it is. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theInfoCrossesTheNetwork(TestContext context) {
        BoardSpaceBlockEntity space = tile(context, new ItemStack(ModItems.POT_CARTRIDGE));
        PotCartridgeItem.setItems(space.getActiveCartridgeItemStack(), List.of(new ItemStack(Items.EMERALD, 3)));
        TileInfo info = TileInfos.of(space);
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        TileInfo.PACKET_CODEC.encode(buf, info);
        context.assertTrue(TileInfo.PACKET_CODEC.decode(buf).sameAs(info), "decoded the same");
        context.complete();
    }

    /**
     * Where a token is heading: the space its steps end on along the single way, check points costing none; a Stop
     * space or a fork ends the way before.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theWayEndsWhereTheStepsDo(TestContext context) {
        TestBoards.floor(context, 14, 6, 0);
        List<BlockPos> path = new ArrayList<>();
        for (int x = 1; x <= 11; x += 2) path.add(new BlockPos(x, 1, 1));
        for (int i = 0; i < path.size(); i++) {
            ItemStack cartridge = new ItemStack(i == 4 ? ModItems.BOARD_SPACE_BEHAVIOR_STOP : ModItems.BOARD_SPACE_BEHAVIOR);
            List<BlockPos> next = i + 1 < path.size() ? List.of(context.getAbsolutePos(path.get(i + 1))) : List.of();
            cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(next), ""));
            tile(context, path.get(i), cartridge);
        }
        BlockPos first = context.getAbsolutePos(path.get(1));
        context.assertEquals(TileInfos.walk(context.getWorld(), first, 1), first, "one step: the first space");
        context.assertEquals(TileInfos.walk(context.getWorld(), first, 2), context.getAbsolutePos(path.get(2)), "two steps");
        context.assertEquals(TileInfos.walk(context.getWorld(), first, 6), context.getAbsolutePos(path.get(4)), "the Stop space ends it");
        // A fork at path 2: the way ends there
        BoardSpaceBlockEntity fork = context.getBlockEntity(path.get(2));
        BlockPos side = new BlockPos(5, 1, 3);
        tile(context, side, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        fork.getActiveCartridgeItemStack().set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(List.of(
                context.getAbsolutePos(path.get(3)), context.getAbsolutePos(side))), ""));
        context.assertEquals(TileInfos.walk(context.getWorld(), first, 5), context.getAbsolutePos(path.get(2)), "a fork ends it");
        List<BoardSpaceDestination> exits = fork.getStockedDestinations();
        context.assertEquals(exits.size(), 2, "two ways out of the fork");
        context.complete();
    }
}
