package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.BlockSlotModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeLayout;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.service.MarkerResidents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.player;
import static fr.lordfinn.steveparty.gametest.DiceTestKit.tile;
import static fr.lordfinn.steveparty.gametest.DiceTestKit.token;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;

/**
 * The Shop Cartridge's disguise: the block its merchant hides in, set in its menu (a block slot beside the small
 * stall, nothing taken), refused for an item that is no block or a block that can't be a box, mentioned in its
 * tooltip; the merchant its space summons wears it (a beehive without one), and so does the merchant living on its
 * « always visible » Spawn Bubble, at once when it changes.
 */
public class ShopDisguiseGameTests implements SteveGameTest {
    private static final BlockPos TILE = new BlockPos(3, 1, 3);
    private static final BlockPos MARKER = new BlockPos(6, 1, 5);

    private static int raw(Item item) {
        return Registries.ITEM.getRawId(item);
    }

    /** A player next to {@code absolute}, allowed to edit the cartridges there. */
    private static ServerPlayerEntity playerNear(TestContext context, BlockPos absolute) {
        ServerPlayerEntity player = player(context);
        Vec3d near = absolute.toCenterPos().add(1.5, 0.5, 0);
        player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
        return player;
    }

    /** Whether {@code text} (its arguments, its siblings) uses the translation key {@code key}. */
    private static boolean uses(Text text, String key) {
        if (text.getContent() instanceof TranslatableTextContent t) {
            if (t.getKey().equals(key)) return true;
            for (Object arg : t.getArgs()) if (arg instanceof Text a && uses(a, key)) return true;
        }
        for (Text sibling : text.getSiblings()) if (uses(sibling, key)) return true;
        return false;
    }

    private static boolean tooltipMentions(ItemStack stack, String key) {
        List<Text> lines = new ArrayList<>();
        Tooltips.forTests(false, false);
        try {
            stack.getItem().appendTooltip(stack, Item.TooltipContext.DEFAULT, lines, TooltipType.BASIC);
        } finally {
            Tooltips.forTests(null);
        }
        return lines.stream().anyMatch(line -> uses(line, key));
    }

    /**
     * Its menu: a block slot beside the small stall (no height of its own: the menu still fits beside a tile), a
     * block put there stored as its default state, an item that is no block or a block that can't be a box refused,
     * a right click (the empty value) back to the default; the tooltip names it only when set.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theDisguiseIsSetInTheMenuAndChecked(TestContext context) {
        ItemStack cartridge = new ItemStack(ModItems.SHOP_CARTRIDGE);
        BoardSpaceBlockEntity space = tile(context, TILE, cartridge);
        ItemStack stored = space.getStack(0);
        BlockSlotModule module = (BlockSlotModule) CartridgeMenus.module(stored, "disguise");
        context.assertTrue(module != null && module.inline() && module.editable(), "a block slot, beside the small stall");
        context.assertEquals(CartridgeLayout.indexOf(CartridgeMenus.modules(stored), BlockSlotModule.class), 1, "right after the offers");
        context.assertEquals(module.get(stored), BlockSlotModule.EMPTY, "empty: the default box");
        context.assertTrue(ShopCartridgeItem.disguise(stored) == ShopCartridgeItem.DEFAULT_DISGUISE, "the default box: a beehive");
        String key = "tooltip.steveparty.shop_cartridge.disguise";
        context.assertFalse(tooltipMentions(stored, key), "no disguise: not in the tooltip");

        CartridgeRef ref = CartridgeRef.slot(space.getPos(), 0);
        ServerPlayerEntity player = playerNear(context, space.getPos());
        context.assertTrue(CartridgeMenus.apply(player, ref, "disguise", raw(Items.OAK_PLANKS)), "a block: accepted");
        context.assertTrue(stored.get(ModComponents.BOX_BLOCK) == Blocks.OAK_PLANKS.getDefaultState(), "stored on the cartridge");
        context.assertEquals(module.get(stored), raw(Items.OAK_PLANKS), "the slot shows it");
        context.assertTrue(ShopCartridgeItem.disguise(stored) == Blocks.OAK_PLANKS.getDefaultState(), "the merchant's box");
        context.assertTrue(tooltipMentions(stored, key), "the tooltip names it");
        context.assertEquals(player.getInventory().count(Items.OAK_PLANKS), 0, "nothing given, nothing taken");

        for (Item refused : List.of(Items.DIAMOND, Items.BARRIER, Items.CHEST, Items.LIGHT)) {
            context.assertFalse(CartridgeMenus.apply(player, ref, "disguise", raw(refused)), refused + ": refused");
            context.assertTrue(stored.get(ModComponents.BOX_BLOCK) == Blocks.OAK_PLANKS.getDefaultState(), refused + ": unchanged");
        }
        context.assertFalse(ShopCartridgeItem.setDisguise(stored, Blocks.AIR), "air can't be a box");

        context.assertTrue(CartridgeMenus.apply(player, ref, "disguise", BlockSlotModule.EMPTY), "emptied");
        context.assertFalse(stored.contains(ModComponents.BOX_BLOCK), "the default is not stored");
        context.assertTrue(ShopCartridgeItem.disguise(stored) == ShopCartridgeItem.DEFAULT_DISGUISE, "the beehive again");
        context.complete();
    }

    /** The merchant a shop space summons wears its cartridge's disguise, a beehive without one. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theSummonedMerchantWearsTheDisguise(TestContext context) {
        ItemStack cartridge = new ItemStack(ModItems.SHOP_CARTRIDGE);
        ShopCartridgeItem item = (ShopCartridgeItem) cartridge.getItem();
        BoxedTraderEntity plain = ModEntities.BOXED_TRADER_ENTITY.create(context.getWorld());
        item.dress(plain, cartridge);
        context.assertTrue(plain.getBlockState() == ShopCartridgeItem.DEFAULT_DISGUISE, "no disguise: a beehive");
        ShopCartridgeItem.setDisguise(cartridge, Blocks.BOOKSHELF);
        BoxedTraderEntity dressed = ModEntities.BOXED_TRADER_ENTITY.create(context.getWorld());
        item.dress(dressed, cartridge);
        context.assertTrue(dressed.getBlockState() == Blocks.BOOKSHELF.getDefaultState(), "a bookshelf: he hides in it");
        context.complete();
    }

    /**
     * « Always visible » on its Spawn Bubble: the merchant living there wears the disguise, and changes it as soon as
     * the cartridge's does (the same merchant, not a new one).
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "shop_disguise_resident")
    public void theResidentMerchantFollowsTheDisguise(TestContext context) {
        ServerPlayerEntity a = player(context), b = player(context);
        ItemStack cartridge = new ItemStack(ModItems.SHOP_CARTRIDGE);
        ShopCartridgeItem.setDisguise(cartridge, Blocks.MELON);
        BoardSpaceBlockEntity space = tile(context, TILE, cartridge);
        context.setBlockState(MARKER.down(), Blocks.STONE);
        context.setBlockState(MARKER, ModBlocks.SPAWN_MARKER.getDefaultState().with(SpawnMarkerBlock.ROTATION, SpawnMarkerBlock.rotation(Direction.SOUTH)));
        BlockPos marker = context.getAbsolutePos(MARKER);
        CartridgeSpawnMarker.set(space.getStack(0), context.getWorld(), marker);
        CartridgeSpawnMarker.own(context.getWorld(), marker, space.getPos());
        ((SpawnMarkerBlockEntity) context.getWorld().getBlockEntity(marker)).setResident(true);
        atEnd(context, () -> MarkerResidents.remove(context.getWorld(), marker));
        List<MobEntity> tokens = new ArrayList<>();
        tokens.add(token(context, new BlockPos(1, 1, 1), a.getUuid()));
        tokens.add(token(context, new BlockPos(3, 1, 1), b.getUuid()));
        DiceTestKit.party(context, a.getUuid(), tokens.toArray(MobEntity[]::new));
        MarkerResidents.update(context.getWorld().getServer());
        MobEntity resident = MarkerResidents.resident(context.getWorld(), marker);
        context.assertTrue(resident instanceof BoxedTraderEntity, "a party: the merchant lives on the bubble");
        BoxedTraderEntity merchant = (BoxedTraderEntity) resident;
        context.assertTrue(merchant.getBlockState() == Blocks.MELON.getDefaultState(), "in the cartridge's disguise");

        ServerPlayerEntity editor = playerNear(context, space.getPos());
        CartridgeRef ref = CartridgeRef.slot(space.getPos(), 0);
        context.assertTrue(CartridgeMenus.apply(editor, ref, "disguise", raw(Items.PUMPKIN)), "changed in the menu");
        context.assertTrue(MarkerResidents.resident(context.getWorld(), marker) == merchant, "the same merchant");
        context.assertTrue(merchant.getBlockState() == Blocks.PUMPKIN.getDefaultState(), "his box changed at once");
        context.assertTrue(CartridgeMenus.apply(editor, ref, "disguise", BlockSlotModule.EMPTY), "emptied");
        context.assertTrue(merchant.getBlockState() == ShopCartridgeItem.DEFAULT_DISGUISE, "his beehive again");
        context.complete();
    }
}
