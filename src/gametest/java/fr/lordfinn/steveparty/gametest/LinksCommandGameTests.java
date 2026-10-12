package fr.lordfinn.steveparty.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.lordfinn.steveparty.commands.LinksCommand;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.board.BrushLinks;
import fr.lordfinn.steveparty.board.LinkTargets;
import fr.lordfinn.steveparty.items.LinkTags;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /steveparty links}: anyone runs it, it explains in the chat how to link (brush, then cartridge / page), every
 * line in both languages; the screens point to it; the items making links are tagged « [Linker] », the blocks
 * they link to « [Linkable] »; the brush numbers the linked storages.
 */
public class LinksCommandGameTests implements SteveGameTest {

    private static JsonObject lang(String code) {
        try (InputStream in = LinksCommandGameTests.class.getResourceAsStream("/assets/steveparty/lang/" + code + ".json")) {
            if (in == null) throw new AssertionError("no lang file " + code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Run by a player without any permission: the whole explanation comes to the chat. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anyoneGetsTheExplanationInTheChat(TestContext context) throws CommandSyntaxException {
        MinecraftServer server = context.getWorld().getServer();
        List<Text> said = new ArrayList<>();
        CommandOutput output = new CommandOutput() {
            @Override
            public void sendMessage(Text message) {
                said.add(message);
            }

            @Override
            public boolean shouldReceiveFeedback() {
                return true;
            }

            @Override
            public boolean shouldTrackOutput() {
                return true;
            }

            @Override
            public boolean shouldBroadcastConsoleToOps() {
                return false;
            }
        };
        ServerCommandSource source = new ServerCommandSource(output, Vec3d.ZERO, Vec2f.ZERO, context.getWorld(), 0,
                "player", Text.literal("player"), server, null);
        int result = server.getCommandManager().getDispatcher().execute(LinksCommand.COMMAND, source);
        context.assertEquals(result, 1, "the command ran");
        context.assertEquals(said.size(), LinksCommand.lines().size(), "every line said: " + said.size());
        context.assertEquals(said.size(), 3 + LinksCommand.BRUSH_LINES + LinksCommand.CLICK_LINES, "a title, two parts");
        context.complete();
    }

    /** Every line of the command, the screens' short hint and the tag exist in English and in French. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyKeyIsTranslated(TestContext context) {
        JsonObject en = lang("en_us"), fr = lang("fr_fr");
        List<String> keys = new ArrayList<>(LinksCommand.keys());
        keys.add("gui.steveparty.links.hint");
        keys.add("tooltip.steveparty.tag.linkable");
        keys.add("tooltip.steveparty.tag.linker");
        for (String key : keys) {
            context.assertTrue(en.has(key) && fr.has(key), "key in both languages: " + key);
        }
        // The command's lines use exactly those keys
        for (Text line : LinksCommand.lines()) {
            String key = keyOf(line);
            context.assertTrue(key != null && keys.contains(key), "a line of the command has a known key: " + line.getString());
        }
        context.assertTrue(fr.get("gui.steveparty.links.hint").getAsString().contains("/" + LinksCommand.COMMAND)
                && en.get("gui.steveparty.links.hint").getAsString().contains("/" + LinksCommand.COMMAND), "the hint names the command");
        context.complete();
    }

    /** The items making links by clicking are tagged « [Linker] », the brush included; plain items and blocks are not. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void linkersAreTagged(TestContext context) {
        for (ItemStack stack : List.of(new ItemStack(ModItems.INVENTORY_CARTRIDGE), new ItemStack(ModItems.MINI_GAME_PAGE),
                new ItemStack(ModItems.SHOPKEEPER_KEY), new ItemStack(ModItems.MINI_GAME_REMOTE), new ItemStack(ModItems.TILE_LINKER_BRUSH))) {
            List<Text> lines = new ArrayList<>(List.of(stack.getName()));
            LinkTags.tag(stack, lines);
            context.assertTrue(lines.size() == 2 && lines.get(1).getString().contains(Tooltips.Tag.LINKER.label().getString()),
                    "tagged Linker: " + stack.getItem());
            context.assertTrue(!LinkTags.isLinkable(stack), "a linker is no target: " + stack.getItem());
        }
        for (ItemStack stack : List.of(new ItemStack(ModItems.WRENCH), new ItemStack(Items.CHEST), new ItemStack(Items.DIRT))) {
            context.assertTrue(!LinkTags.isLinker(stack), "no linker: " + stack.getItem());
        }
        context.complete();
    }

    /**
     * The blocks the link code takes as a target are tagged « [Linkable] »: storages (vanilla ones too), board spaces,
     * the Spawn Bubble, pipes, podiums, goal pole bases, step controllers, the trading stall, the cash register, the
     * Mini-game Controller, the Party Controller and the board's holders (a Step Controller drives their party); not a
     * mini-game pipe (a page programs it), not plain blocks.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void targetsAreTaggedLinkable(TestContext context) {
        List<Item> linkable = List.of(Items.CHEST, Items.TRAPPED_CHEST, Items.BARREL, Items.SHULKER_BOX, Items.HOPPER, Items.FURNACE,
                ModBlocks.TILE.asItem(), ModBlocks.SPAWN_MARKER.asItem(), ModBlocks.PIPES[0][0].asItem(), ModBlocks.PODIUM.asItem(),
                ModBlocks.GOAL_POLE_BASE.asItem(), ModBlocks.STEP_CONTROLLER.asItem(), ModBlocks.TRADING_STALL.asItem(),
                ModBlocks.CASH_REGISTER.asItem(), ModBlocks.MINI_GAME_CONTROLLER.asItem(), ModBlocks.PARTY_CONTROLLER.asItem(),
                ModBlocks.HOP_SWITCH.asItem(), ModBlocks.LOOTING_BOX.asItem(), ModBlocks.PIGGY_BANK.asItem());
        for (Item item : linkable) {
            ItemStack stack = new ItemStack(item);
            List<Text> lines = new ArrayList<>(List.of(stack.getName()));
            LinkTags.tag(stack, lines);
            context.assertTrue(lines.size() == 2 && lines.get(1).getString().contains(Tooltips.Tag.LINKABLE.label().getString()),
                    "tagged Linkable: " + item);
        }
        List<Item> not = new ArrayList<>(List.of(Items.DIRT, Items.STONE_BUTTON));
        for (PipeKind kind : PipeKind.values()) {
            if (kind.isMiniGame()) for (Block block : ModBlocks.PIPES[kind.ordinal()]) not.add(block.asItem());
        }
        for (Item item : not) context.assertTrue(!LinkTags.isLinkable(new ItemStack(item)), "not Linkable: " + item);
        // The item says what the placed block answers
        BlockPos chest = new BlockPos(1, 1, 1);
        context.setBlockState(chest, Blocks.BARREL);
        context.assertTrue(LinkTargets.accepts(context.getWorld(), context.getAbsolutePos(chest))
                && CartridgeContainers.accepts(context.getWorld(), context.getAbsolutePos(chest)), "a placed barrel is a target");
        context.complete();
    }

    /**
     * The brush's storage marks: a chest linked by a tile's cartridge gets its number in that cartridge's list, a chest
     * linked by nothing none.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void linkedStoragesAreNumbered(TestContext context) {
        BlockPos first = new BlockPos(3, 1, 1), second = new BlockPos(3, 1, 3), alone = new BlockPos(1, 1, 3);
        for (BlockPos pos : List.of(first, second, alone)) context.setBlockState(pos, Blocks.CHEST);
        ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        RegistryKey<World> dimension = context.getWorld().getRegistryKey();
        CartridgeContainers.set(cartridge, List.of(GlobalPos.create(dimension, context.getAbsolutePos(second)),
                GlobalPos.create(dimension, context.getAbsolutePos(first))));
        BlockPos tile = new BlockPos(1, 1, 1);
        context.setBlockState(tile.down(), Blocks.STONE);
        context.setBlockState(tile, ModBlocks.TILE);
        BoardSpaceBlockEntity space = context.getBlockEntity(tile);
        space.setStack(0, cartridge);
        Map<BlockPos, Integer> numbers = BrushLinks.linkedStorages(context.getWorld(), Vec3d.ofCenter(context.getAbsolutePos(tile)), 16);
        context.assertTrue(Integer.valueOf(1).equals(numbers.get(context.getAbsolutePos(second)))
                && Integer.valueOf(2).equals(numbers.get(context.getAbsolutePos(first))), "numbered in the cartridge's order: " + numbers);
        context.assertTrue(!numbers.containsKey(context.getAbsolutePos(alone)), "a chest linked by nothing has no number");
        context.complete();
    }

    /** The key of a line (its first translated part). */
    private static String keyOf(Text line) {
        if (line.getContent() instanceof TranslatableTextContent t) return t.getKey();
        for (Text sibling : line.getSiblings()) {
            String key = keyOf(sibling);
            if (key != null) return key;
        }
        return null;
    }
}
