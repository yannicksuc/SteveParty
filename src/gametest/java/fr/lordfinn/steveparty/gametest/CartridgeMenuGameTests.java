package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ReplayBoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.StopBoardSpaceBehavior;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TeleportNetwork;
import fr.lordfinn.steveparty.components.TeleportSettingsComponent;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeLayout;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ColorModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import fr.lordfinn.steveparty.payloads.custom.CartridgeSettingPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.BoardSpaceScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.CartridgeScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.GhostSlot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import io.netty.buffer.Unpooled;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The cartridge menus: every cartridge declares modules that fit the shell, a change is checked (who, where, which
 * module, which value) then written, each cartridge's settings go and come back through its modules, and the Inventory
 * Cartridge's ghost slots work in hand and in a tile.
 */
public class CartridgeMenuGameTests implements FabricGameTest {
    private static final BlockPos TILE = new BlockPos(2, 1, 2);

    private static final List<Item> CARTRIDGES = List.of(ModItems.BOARD_SPACE_BEHAVIOR, ModItems.TILE_BEHAVIOR_START,
            ModItems.BOARD_SPACE_BEHAVIOR_STOP, ModItems.INVENTORY_CARTRIDGE, ModItems.SHOP_CARTRIDGE,
            ModItems.ADVANCE_BACK_CARTRIDGE, ModItems.REPLAY_CARTRIDGE, ModItems.TELEPORT_CARTRIDGE, ModItems.STAR_CARTRIDGE,
            ModItems.GLANDOUILLE_CARTRIDGE, ModItems.FROUSSEUX_CARTRIDGE, ModItems.MISTIGRI_CARTRIDGE);

    private static BoardSpaceBlockEntity tile(TestContext context, net.minecraft.block.Block block, ItemStack cartridge) {
        context.setBlockState(TILE.down(), Blocks.STONE);
        context.setBlockState(TILE, block);
        BoardSpaceBlockEntity tile = context.getBlockEntity(TILE);
        tile.setStack(0, cartridge);
        return tile;
    }

    private static ServerPlayerEntity playerNear(TestContext context, BlockPos absolute) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        Vec3d near = absolute.toCenterPos().add(1.5, 0.5, 0);
        player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
        return player;
    }

    private static void remove(TestContext context, ServerPlayerEntity player) {
        TestPlayers.remove(context, player);
    }

    // ------------------------------------------------------------------ texts: do they fit?

    private static JsonObject lang(String code) {
        try (InputStream in = CartridgeMenuGameTests.class.getResourceAsStream("/assets/steveparty/lang/" + code + ".json")) {
            if (in == null) throw new AssertionError("no lang file " + code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * The width of {@code text} in the game's default font, glyph by glyph (the server has no font): the advance of
     * each ASCII glyph, 6 for a letter with an accent, 9 (more than any) for the other symbols.
     */
    private static int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ("i!.,:;|'".indexOf(c) >= 0) width += 2;
            else if ("l`".indexOf(c) >= 0) width += 3;
            else if (" It[]îï".indexOf(c) >= 0) width += 4;
            else if ("fk<>(){}*\"".indexOf(c) >= 0) width += 5;
            else if ("@~".indexOf(c) >= 0) width += 7;
            else if (c < 0x7F || Character.isLetter(c) || c == '°') width += 6;
            else width += 9;
        }
        return Math.max(0, width - 1);
    }

    /** The lines {@code text} takes in {@code max} pixels, wrapped on its spaces like the game does. */
    private static int lines(String text, int max) {
        int lines = 1, line = 0;
        for (String word : text.split(" ")) {
            int w = width(word);
            if (line > 0 && line + 4 + w > max) {
                lines++;
                line = w;
            } else {
                line += (line > 0 ? 4 : 0) + w;
            }
        }
        return lines;
    }

    /** {@code text} in the language {@code lang} (its translation keys and their arguments resolved). */
    private static String resolve(Text text, JsonObject lang) {
        StringBuilder out = new StringBuilder();
        if (text.getContent() instanceof TranslatableTextContent translatable) {
            String pattern = lang.has(translatable.getKey()) ? lang.get(translatable.getKey()).getAsString() : translatable.getKey();
            for (Object arg : translatable.getArgs()) {
                String value = arg instanceof Text nested ? resolve(nested, lang) : String.valueOf(arg);
                pattern = pattern.replaceFirst("%(\\d+\\$)?[sd]", java.util.regex.Matcher.quoteReplacement(value));
            }
            out.append(pattern);
        } else {
            out.append(text.copyContentOnly().getString());
        }
        for (Text sibling : text.getSiblings()) out.append(resolve(sibling, lang));
        return out.toString();
    }

    private static String translate(JsonObject lang, String key, TestContext context) {
        context.assertTrue(lang.has(key), "missing text " + key);
        return lang.get(key).getAsString();
    }

    /**
     * The height the menu gives a module for {@code stack} (the client measures the same with the font): its title,
     * and its widgets or its texts wrapped on as many lines as they need.
     */
    private static int measuredHeight(CartridgeModule module, ItemStack stack, InfoModule.Context info, JsonObject lang, int columnW) {
        int label = module.labelKey() == null ? 0 : CartridgeModule.LABEL_H;
        return label + switch (module) {
            case ChoiceModule choice -> choice.swatches() ? ChoiceModule.SWATCH_H : ChoiceModule.BUTTON_H;
            case NumberModule number -> NumberModule.ROW_H;
            case ColorModule color -> {
                int perRow = (columnW + ColorModule.GAP) / (ColorModule.SWATCH + ColorModule.GAP);
                int rows = (ColorModule.DEFAULT + perRow) / perRow;
                yield rows * ColorModule.SWATCH + (rows - 1) * ColorModule.GAP;
            }
            case GhostSlotsModule ghosts -> Math.max(54, 26 + 10 * lines(lang.get(CartridgeItem.MENU_KEY + "inventory.wheel").getAsString(), columnW - 60));
            case InfoModule infoModule -> {
                int width = columnW - (infoModule.hasIcon() ? InfoModule.ICON + 4 : 0), count = 0;
                for (InfoModule.Line line : infoModule.content(info)) count += lines(resolve(line.text(), lang), width);
                yield Math.max(count * InfoModule.LINE_H, infoModule.hasIcon() ? InfoModule.ICON + 2 : 0);
            }
            default -> module.height();
        };
    }

    /**
     * Every cartridge, in French and in English, in every state of its texts: the one-line texts (short name, module
     * titles, buttons) fit their box without being cut, and with its texts wrapped on all the lines they need the menu
     * fits the smallest GUI (320 × 240) in hand and beside a tile, without scrolling.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyCartridgeTextFitsInFrenchAndEnglish(TestContext context) {
        BoardSpaceBlockEntity teleportTile = tile(context, ModBlocks.TILE, new ItemStack(ModItems.TELEPORT_CARTRIDGE));
        int columnW = CartridgeLayout.COLUMN_W;
        for (String code : List.of("fr_fr", "en_us")) {
            JsonObject lang = lang(code);
            for (Item item : CARTRIDGES) {
                List<CartridgeModule> modules = ((CartridgeItem) item).modules();
                String id = Registries.ITEM.getId(item).getPath();
                String name = code + " " + id;
                context.assertTrue(!modules.isEmpty(), name + ": has modules");
                Set<String> ids = new HashSet<>();
                for (CartridgeModule module : modules) context.assertTrue(ids.add(module.id()), name + ": unique id " + module.id());

                // The label: icon (22) + name + lightning button (17), in a one-column shell
                int labelRoom = CartridgeLayout.width(1, columnW) - 12 - 22 - 21;
                String shortName = translate(lang, CartridgeItem.MENU_KEY + "name." + id, context);
                context.assertTrue(width(shortName) <= labelRoom - width("n°16") - 4, name + ": short name « " + shortName + " » fits beside a slot number");

                // The states its texts depend on
                List<ItemStack> states = new java.util.ArrayList<>(List.of(new ItemStack(item)));
                ItemStack other = new ItemStack(item);
                other.set(ModComponents.INVENTORY_POS, new BlockPos(-29999999, -64, -29999999));
                other.set(ModComponents.SHOP_LINK, new fr.lordfinn.steveparty.components.ShopLinkComponent(java.util.UUID.randomUUID(), new BlockPos(-29999999, -64, -29999999)));
                other.set(ModComponents.TB_START_OWNER, java.util.UUID.randomUUID().toString());
                other.set(ModComponents.TB_START_BOUND_ENTITY, java.util.UUID.randomUUID().toString());
                states.add(other);
                for (ItemStack stack : states) {
                    for (BlockPos pos : new BlockPos[]{null, teleportTile.getPos()}) {
                        InfoModule.Context info = new InfoModule.Context(stack, context.getWorld(), pos);
                        int[] heights = new int[modules.size()];
                        for (int i = 0; i < heights.length; i++) {
                            CartridgeModule module = modules.get(i);
                            heights[i] = measuredHeight(module, stack, info, lang, columnW);
                            if (module.labelKey() != null) {
                                String title = translate(lang, module.labelKey(), context);
                                int titleRoom = columnW - 19;
                                if (module instanceof ChoiceModule choice && choice.swatches()) {
                                    for (ChoiceModule.Option option : choice.options()) {
                                        context.assertTrue(width(title + ": " + translate(lang, option.key(), context)) <= titleRoom,
                                                name + ": title « " + title + " » with its choice fits");
                                    }
                                } else {
                                    context.assertTrue(width(title) <= titleRoom, name + ": title « " + title + " » fits (" + width(title) + " > " + titleRoom + ")");
                                }
                            }
                            if (module instanceof ChoiceModule choice && !choice.swatches()) {
                                int n = choice.options().size();
                                int room = (columnW - (n - 1) * 3) / n - 6;
                                for (ChoiceModule.Option option : choice.options()) {
                                    String text = translate(lang, option.key(), context);
                                    context.assertTrue(width(text) <= room, name + ": button « " + text + " » fits (" + width(text) + " > " + room + ")");
                                }
                            }
                        }
                        boolean ghosts = CartridgeLayout.indexOf(modules, GhostSlotsModule.class) >= 0;
                        // Beside a tile: one column, within the height under the tile's top (240 high: 28 above)
                        CartridgeLayout beside = CartridgeLayout.of(modules, CartridgeLayout.MAX_CONTENT_BESIDE_TILE, heights, 1, columnW);
                        context.assertTrue(beside.height() <= 240 - 28 - 4, name + ": fits beside the tile without scrolling (" + beside.height() + ")");
                        // In hand: alone, or over the inventory in the shell of a fixed size (two columns)
                        if (ghosts) {
                            CartridgeLayout inHand = CartridgeLayout.of(modules, CartridgeLayout.MAX_CONTENT_WITH_INVENTORY, heights, 2, columnW);
                            context.assertTrue(inHand.height() <= CartridgeLayout.SHELL_H_WITH_INVENTORY && inHand.width() <= 320,
                                    name + ": fits over the inventory (" + inHand.width() + "x" + inHand.height() + ")");
                        } else {
                            CartridgeLayout inHand = CartridgeLayout.of(modules, CartridgeLayout.MAX_CONTENT_ALONE, heights, 2, columnW);
                            context.assertTrue(inHand.height() <= 240 - 8 && inHand.width() <= 320 - 8,
                                    name + ": fits in hand (" + inHand.width() + "x" + inHand.height() + ")");
                        }
                    }
                }
            }
            // The empty slot's hint: all its lines in the empty shell
            int hint = lines(translate(lang, CartridgeItem.MENU_KEY + "empty.hint", context), columnW);
            context.assertTrue(hint * InfoModule.LINE_H <= 183 - CartridgeLayout.TOP - CartridgeLayout.BOTTOM, code + ": empty hint fits");
        }
        // The ghost slots are the Inventory Cartridge's first module: the screen handlers place them there
        context.assertTrue(((CartridgeItem) ModItems.INVENTORY_CARTRIDGE).modules().getFirst() instanceof GhostSlotsModule, "ghost slots first");
        // A narrow window: a narrower column, and one column only when a second would not fit (the shell scrolls)
        List<CartridgeModule> teleport = ((CartridgeItem) ModItems.TELEPORT_CARTRIDGE).modules();
        int[] tall = new int[teleport.size()];
        java.util.Arrays.fill(tall, 60);
        CartridgeLayout narrow = CartridgeLayout.of(teleport, 100, tall, 1, CartridgeLayout.MIN_COLUMN_W);
        context.assertEquals(narrow.columns(), 1, "one column when there is no room for two");
        context.assertEquals(narrow.width(), CartridgeLayout.width(1, CartridgeLayout.MIN_COLUMN_W), "a narrow shell");
        context.assertEquals(CartridgeLayout.of(teleport, 100, tall, 2, columnW).columns(), 2, "two columns when there is room");
        context.complete();
    }

    /** A change is written only for a player allowed there, a known module that can change now, and a valid value. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void changesAreCheckedThenWritten(TestContext context) {
        BoardSpaceBlockEntity tile = tile(context, ModBlocks.TILE, new ItemStack(ModItems.ADVANCE_BACK_CARTRIDGE));
        BlockPos pos = tile.getPos();
        CartridgeRef ref = CartridgeRef.slot(pos, 0);
        ServerPlayerEntity player = playerNear(context, pos);
        try {
            context.assertTrue(CartridgeMenus.apply(player, ref, "steps", 5), "allowed");
            context.assertEquals(AdvanceBackCartridgeItem.steps(tile.getStack(0)), 5, "written in the tile's cartridge");
            context.assertTrue(!CartridgeMenus.apply(player, ref, "steps", 7), "out of range: refused");
            context.assertTrue(!CartridgeMenus.apply(player, ref, "steps", 0), "under the range: refused");
            context.assertTrue(!CartridgeMenus.apply(player, ref, "nope", 1), "unknown module: refused");
            context.assertTrue(!CartridgeMenus.apply(player, ref, "hint", 0), "a read-only module: refused");
            context.assertTrue(!CartridgeMenus.apply(player, CartridgeRef.slot(pos, 3), "steps", 2), "no such slot: refused");
            player.changeGameMode(GameMode.ADVENTURE);
            context.assertTrue(!CartridgeMenus.apply(player, ref, "steps", 2), "adventure: refused");
            player.changeGameMode(GameMode.SPECTATOR);
            context.assertTrue(!CartridgeMenus.apply(player, ref, "steps", 2), "spectator: refused");
            player.changeGameMode(GameMode.SURVIVAL);
            Vec3d far = pos.toCenterPos().add(20, 0.5, 0);
            player.refreshPositionAndAngles(far.x, far.y, far.z, 0, 0);
            context.assertTrue(!CartridgeMenus.apply(player, ref, "steps", 2), "out of reach: refused");
            context.assertEquals(AdvanceBackCartridgeItem.steps(tile.getStack(0)), 5, "unchanged");

            // A module that can't change now: the next space's effect, when the token stays on the Teleport tile
            ItemStack teleport = new ItemStack(ModItems.TELEPORT_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, teleport);
            CartridgeRef hand = CartridgeRef.hand(Hand.MAIN_HAND);
            context.assertTrue(!CartridgeMenus.apply(player, hand, "triggers", 1), "no current to « triggers » while staying");
            context.assertTrue(CartridgeMenus.apply(player, hand, "arrival", 1), "moving on");
            context.assertTrue(CartridgeMenus.apply(player, hand, "triggers", 1), "now it can");
            context.assertTrue(!TeleportCartridgeItem.settings(teleport).pushTriggers(), "the next space does nothing");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            context.assertTrue(!CartridgeMenus.apply(player, hand, "arrival", 0), "not a cartridge: refused");
        } finally {
            remove(context, player);
        }
        context.complete();
    }

    /** The payload: sent back and forth intact, applied only to the menu open in that screen. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void payloadGoesToTheOpenMenuOnly(TestContext context) {
        CartridgeSettingPayload payload = new CartridgeSettingPayload(7, "purchases", 4);
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        CartridgeSettingPayload.CODEC.encode(buf, payload);
        context.assertEquals(CartridgeSettingPayload.CODEC.decode(buf), payload, "sent");

        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            ItemStack shop = new ItemStack(ModItems.SHOP_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, shop);
            new CartridgeSettingPayload(player.currentScreenHandler.syncId, "purchases", 4).handle(player);
            context.assertEquals(ShopCartridgeItem.purchases(shop), ShopCartridgeItem.DEFAULT_PURCHASES, "no menu open: ignored");

            CartridgeScreenHandler menu = new CartridgeScreenHandler(42, player.getInventory(), CartridgeRef.hand(Hand.MAIN_HAND));
            player.currentScreenHandler = menu;
            new CartridgeSettingPayload(41, "purchases", 4).handle(player);
            context.assertEquals(ShopCartridgeItem.purchases(shop), ShopCartridgeItem.DEFAULT_PURCHASES, "another screen: ignored");
            new CartridgeSettingPayload(42, "purchases", 4).handle(player);
            context.assertEquals(ShopCartridgeItem.purchases(shop), 4, "its menu: applied");
            new CartridgeSettingPayload(42, "purchases", 99).handle(player);
            context.assertEquals(ShopCartridgeItem.purchases(shop), 4, "invalid value: ignored");
            context.assertTrue(menu.canUse(player), "the menu stays open while the cartridge is in hand");
            context.assertTrue(!menu.withInventory() && menu.slots.isEmpty(), "no inventory without ghost slots");
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            context.assertTrue(!menu.canUse(player), "the cartridge gone: the menu closes");
            player.currentScreenHandler = player.playerScreenHandler;
        } finally {
            remove(context, player);
        }
        context.complete();
    }

    /** Each cartridge's settings through its modules: every value written reads back, in hand and in a tile. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyModuleRoundTrips(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            CartridgeRef hand = CartridgeRef.hand(Hand.MAIN_HAND);
            for (Item item : CARTRIDGES) {
                ItemStack stack = new ItemStack(item);
                player.setStackInHand(Hand.MAIN_HAND, stack);
                for (CartridgeModule module : ((CartridgeItem) item).modules()) {
                    if (!module.editable()) continue;
                    int[] values = switch (module) {
                        case ChoiceModule choice -> java.util.stream.IntStream.range(0, choice.options().size()).toArray();
                        case NumberModule number -> java.util.stream.IntStream.rangeClosed(number.min(), number.max()).toArray();
                        case ColorModule color -> java.util.stream.IntStream.rangeClosed(0, ColorModule.DEFAULT).toArray();
                        default -> new int[0];
                    };
                    // Walk every value, then back to the first (a module that can't change now is skipped)
                    for (int value : values) {
                        if (!module.enabled(stack)) break;
                        context.assertTrue(CartridgeMenus.apply(player, hand, module.id(), value), item + " " + module.id() + " = " + value);
                        context.assertEquals(module.get(stack), value, item + " " + module.id() + " reads back");
                    }
                }
            }

            // What the modules write: the same components as the other ways of setting them
            ItemStack advance = new ItemStack(ModItems.ADVANCE_BACK_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, advance);
            CartridgeMenus.apply(player, hand, "steps", 4);
            CartridgeMenus.apply(player, hand, "direction", 0);
            context.assertEquals(AdvanceBackCartridgeItem.steps(advance), -4, "back 4");
            CartridgeMenus.apply(player, hand, "steps", 2);
            context.assertEquals(AdvanceBackCartridgeItem.steps(advance), -2, "still back");
            AdvanceBackCartridgeItem.scroll(advance, 1);
            context.assertEquals(AdvanceBackCartridgeItem.steps(advance), -1, "sneak + wheel still works");

            ItemStack shop = new ItemStack(ModItems.SHOP_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, shop);
            CartridgeMenus.apply(player, hand, "purchases", 3);
            context.assertEquals(shop.get(ModComponents.SHOP_PURCHASES), 3, "3 purchases");
            CartridgeMenus.apply(player, hand, "purchases", ShopCartridgeItem.DEFAULT_PURCHASES);
            context.assertTrue(!shop.contains(ModComponents.SHOP_PURCHASES), "the default is not stored");
            ShopCartridgeItem.scroll(player, shop, 1);
            context.assertEquals(ShopCartridgeItem.purchases(shop), 2, "sneak + wheel still works");

            ItemStack inventory = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, inventory);
            CartridgeMenus.apply(player, hand, "mode", InventoryCartridgeItem.CYCLE);
            context.assertEquals(InventoryCartridgeItem.getSelectionState(inventory), InventoryCartridgeItem.CYCLE, "cycle");

            ItemStack stop = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP);
            player.setStackInHand(Hand.MAIN_HAND, stop);
            CartridgeMenus.apply(player, hand, "color", DyeColor.LIME.getId());
            context.assertEquals(stop.get(ModComponents.COLOR), DyeColor.LIME.getEntityColor(), "lime stop");
            CartridgeMenus.apply(player, hand, "color", ColorModule.DEFAULT);
            context.assertEquals(stop.get(ModComponents.COLOR), StopBoardSpaceBehavior.COLOR & 0xFFFFFF, "its own colour back");
            context.assertEquals(new ColorModule("c", "c", ReplayBoardSpaceBehavior.COLOR).get(new ItemStack(ModItems.REPLAY_CARTRIDGE)),
                    ColorModule.DEFAULT, "a new Replay Cartridge: its own colour");

            // On a tile: the tile takes the changes (its colour follows the network)
            BoardSpaceBlockEntity tile = tile(context, ModBlocks.TILE, new ItemStack(ModItems.TELEPORT_CARTRIDGE));
            Vec3d near = tile.getPos().toCenterPos().add(1.5, 0.5, 0);
            player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
            CartridgeRef onTile = CartridgeRef.slot(tile.getPos(), 0);
            context.assertTrue(CartridgeMenus.apply(player, onTile, "network", TeleportNetwork.BLUE.ordinal()), "blue network");
            context.assertEquals(TeleportCartridgeItem.settings(tile.getStack(0)),
                    TeleportSettingsComponent.DEFAULT.withNetwork(TeleportNetwork.BLUE), "written in the tile");
            context.assertEquals(tile.getStack(0).get(ModComponents.COLOR), TeleportNetwork.BLUE.color(), "blue tile");
        } finally {
            remove(context, player);
        }
        context.complete();
    }

    /**
     * The ghost slots: a copy of an item (the real one stays), a quantity with the wheel, written into the cartridge;
     * in a tile's interface only while an Inventory Cartridge is selected, never filled by a shift-click.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ghostSlotsInHandAndInTiles(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, cartridge);
            CartridgeScreenHandler menu = new CartridgeScreenHandler(3, player.getInventory(), CartridgeRef.hand(Hand.MAIN_HAND));
            player.currentScreenHandler = menu;
            context.assertTrue(menu.withInventory(), "the inventory shown with the ghost slots");
            Slot ghost = menu.slots.getFirst();
            context.assertTrue(ghost instanceof GhostSlot, "ghost slots first");
            menu.setCursorStack(new ItemStack(Items.DIAMOND, 5));
            menu.onSlotClick(0, 0, SlotActionType.PICKUP, player);
            context.assertEquals(menu.getCursorStack().getCount(), 5, "the real diamonds stay on the cursor");
            menu.setCursorStack(ItemStack.EMPTY);
            menu.handleGhostScroll(player, 0, 1);
            menu.handleGhostScroll(player, 0, 1);
            ItemStack stored = cartridge.get(ModComponents.INVENTORY_COMPONENT).getStack(0);
            context.assertTrue(stored.isOf(Items.DIAMOND) && stored.getCount() == 3, "3 diamonds given, written in the cartridge: " + stored);
            player.currentScreenHandler = player.playerScreenHandler;

            // In an Advanced Tile: slot 2 holds the Inventory Cartridge, slot 0 a Stop
            BoardSpaceBlockEntity tile = tile(context, ModBlocks.ADVANCED_TILE, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
            ItemStack inTile = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
            tile.setStack(2, inTile);
            Vec3d near = tile.getPos().toCenterPos().add(1.5, 0.5, 0);
            player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
            BoardSpaceScreenHandler handler = new BoardSpaceScreenHandler(4, player.getInventory(), tile);
            player.currentScreenHandler = handler;
            int firstGhost = -1;
            for (Slot slot : handler.slots) if (slot instanceof GhostSlot && firstGhost < 0) firstGhost = slot.id;
            context.assertTrue(firstGhost > 16 + 35, "ghost slots after the player's");
            context.assertTrue(!handler.slots.get(firstGhost).isEnabled(), "a Stop selected: no ghost slots");
            context.assertEquals(handler.editedCartridge(player), CartridgeRef.slot(tile.getPos(), 0), "the active slot selected");
            context.assertTrue(handler.onButtonClick(player, 2), "select slot 2");
            context.assertTrue(!handler.onButtonClick(player, 16), "no slot 16");
            context.assertTrue(handler.slots.get(firstGhost).isEnabled(), "the Inventory Cartridge selected: its ghost slots");
            handler.setCursorStack(new ItemStack(Items.EMERALD));
            handler.onSlotClick(firstGhost + 4, 0, SlotActionType.PICKUP, player);
            handler.setCursorStack(ItemStack.EMPTY);
            context.assertTrue(inTile.get(ModComponents.INVENTORY_COMPONENT).getStack(4).isOf(Items.EMERALD), "written in the tile's cartridge");
            // A shift-click on a cartridge of the tile goes to the player's inventory, never to a ghost slot
            player.getInventory().clear();
            handler.quickMove(player, 0);
            context.assertTrue(handler.slots.get(firstGhost).getStack().isEmpty(), "no stop in the ghost slots");
            context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP)), "the stop in the inventory");
            // A player not allowed there: the ghost slots don't change
            player.changeGameMode(GameMode.ADVENTURE);
            handler.setCursorStack(new ItemStack(Items.APPLE));
            handler.onSlotClick(firstGhost + 5, 0, SlotActionType.PICKUP, player);
            handler.setCursorStack(ItemStack.EMPTY);
            context.assertTrue(inTile.get(ModComponents.INVENTORY_COMPONENT).getStack(5).isEmpty(), "adventure: no change");
            player.currentScreenHandler = player.playerScreenHandler;
        } finally {
            remove(context, player);
        }
        context.complete();
    }
}
