package fr.lordfinn.steveparty.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.lordfinn.steveparty.commands.LinksCommand;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.items.LinkableItems;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /steveparty links}: anyone runs it, it explains in the chat how to link (brush, then cartridge / page), every
 * line in both languages; the screens point to it; the items holding links are tagged « [Linkable] ».
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

    /** The items whose stacks hold links are tagged; the brush (keeps none) and plain items are not. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itemsHoldingLinksAreTagged(TestContext context) {
        for (ItemStack stack : List.of(new ItemStack(ModItems.INVENTORY_CARTRIDGE), new ItemStack(ModItems.MINI_GAME_PAGE),
                new ItemStack(ModItems.SHOPKEEPER_KEY), new ItemStack(ModItems.MINI_GAME_REMOTE))) {
            List<Text> lines = new ArrayList<>(List.of(stack.getName()));
            LinkableItems.tag(stack, lines);
            context.assertTrue(lines.size() == 2 && lines.get(1).getString().contains(Tooltips.Tag.LINKABLE.label().getString()),
                    "tagged: " + stack.getItem());
        }
        for (ItemStack stack : List.of(new ItemStack(ModItems.TILE_LINKER_BRUSH), new ItemStack(ModItems.WRENCH), new ItemStack(Items.CHEST))) {
            context.assertTrue(!LinkableItems.holdsLinks(stack), "not tagged: " + stack.getItem());
        }
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
