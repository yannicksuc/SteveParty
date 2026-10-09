package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.tooltip.ItemTips;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Every item's tooltip, with and without Shift, is written in both languages, its tags on one line under the name. */
public class TooltipGameTests implements SteveGameTest {

    private static JsonObject lang(String code) {
        try (InputStream in = TooltipGameTests.class.getResourceAsStream("/assets/steveparty/lang/" + code + ".json")) {
            if (in == null) throw new AssertionError("no lang file " + code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** The translation keys of {@code text}, its siblings and its arguments. */
    private static void keys(Text text, Set<String> keys) {
        if (text.getContent() instanceof TranslatableTextContent t) {
            keys.add(t.getKey());
            for (Object arg : t.getArgs()) if (arg instanceof Text a) keys(a, keys);
        }
        for (Text sibling : text.getSiblings()) keys(sibling, keys);
    }

    private static List<Text> tooltip(ItemStack stack, boolean more) {
        return tooltip(stack, more, false);
    }

    private static List<Text> tooltip(ItemStack stack, boolean more, boolean cut) {
        List<Text> lines = new ArrayList<>();
        lines.add(stack.getName());
        Tooltips.forTests(more, cut);
        try {
            stack.getItem().appendTooltip(stack, Item.TooltipContext.DEFAULT, lines, TooltipType.BASIC);
            ItemTips.append(stack, lines);
        } finally {
            Tooltips.forTests(null);
        }
        return lines;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyTooltipKeyIsTranslated(TestContext context) {
        JsonObject en = lang("en_us"), fr = lang("fr_fr");
        Set<String> missing = new TreeSet<>();
        int items = 0;
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if (!id.getNamespace().equals(Steveparty.MOD_ID)) continue;
            items++;
            ItemStack stack = new ItemStack(item);
            Set<String> keys = new TreeSet<>();
            for (boolean more : new boolean[]{false, true}) {
                for (Text line : tooltip(stack, more)) keys(line, keys);
            }
            for (String key : keys) {
                boolean ours = key.contains("steveparty");
                if (ours && (!en.has(key) || !fr.has(key))) missing.add(key + " (" + id.getPath() + ")");
            }
        }
        context.assertTrue(items > 100, "the mod's items were walked: " + items);
        context.assertTrue(missing.isEmpty(), "tooltip keys missing in en_us or fr_fr: " + missing);
        context.complete();
    }

    /** The tags are one line, right under the name; the Shift hint shows only when there is more to show. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tagsUnderTheNameAndShiftHint(TestContext context) {
        ItemStack cartridge = new ItemStack(Registries.ITEM.get(Steveparty.id("star_cartridge")));
        List<Text> lines = tooltip(cartridge, false);
        Set<String> tagLine = new TreeSet<>();
        keys(lines.get(1), tagLine);
        context.assertTrue(tagLine.equals(Set.of("tooltip.steveparty.tag.configurable")),
                "only the tags that say something: no [Cartridge] on a Star Cartridge, got " + tagLine);
        Set<String> last = new TreeSet<>();
        keys(lines.getLast(), last);
        context.assertTrue(last.contains("tooltip.steveparty.more"), "the Shift hint last");
        List<Text> shifted = tooltip(cartridge, true);
        Set<String> all = new TreeSet<>();
        for (Text line : shifted) keys(line, all);
        context.assertTrue(!all.contains("tooltip.steveparty.more") && all.contains("tooltip.steveparty.section.use"),
                "with Shift: the sections, no hint");
        // Two tag sources (the item and a later callback) share the one line
        List<Text> badges = new ArrayList<>(List.of(Text.literal("name")));
        Tooltips.tag(badges, Tooltips.Tag.SWITCHABLE);
        Tooltips.tag(badges, Tooltips.Tag.STAMPED);
        context.assertEquals(badges.size(), 2, "one tag line");
        // Every item given a tooltip without a class of its own exists
        context.assertTrue(ItemTips.all().size() > 50, "plain items with a tooltip: " + ItemTips.all().size());
        context.complete();
    }

    /**
     * Short tooltips: without Shift, at most 3 lines under the name (the Shift hint aside) for nearly every item, never
     * more than 5; no crafting anywhere (REI shows the recipes); a tag never says what the name already says.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tooltipsStayShort(TestContext context) {
        List<String> tooLong = new ArrayList<>(), overBudget = new ArrayList<>(), crafting = new ArrayList<>(), redundant = new ArrayList<>();
        int items = 0;
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if (!id.getNamespace().equals(Steveparty.MOD_ID)) continue;
            items++;
            ItemStack stack = new ItemStack(item);
            List<Text> lines = tooltip(stack, false, true);
            int count = 0;
            for (int i = 1; i < lines.size(); i++) {
                Set<String> keys = new TreeSet<>();
                keys(lines.get(i), keys);
                if (!keys.contains("tooltip.steveparty.more")) count++;
            }
            if (count > 3) overBudget.add(id.getPath() + "=" + count);
            if (count > 5) tooLong.add(id.getPath() + "=" + count);
            Set<String> all = new TreeSet<>();
            for (Text line : tooltip(stack, true)) keys(line, all);
            for (String key : all) if (key.matches(".*[._]craft.*")) crafting.add(id.getPath() + ": " + key);
            if (lines.size() > 1) {
                Set<String> tags = new TreeSet<>();
                keys(lines.get(1), tags);
                String name = stack.getName().getString().toLowerCase(java.util.Locale.ROOT);
                for (Tooltips.Tag tag : Tooltips.Tag.values()) {
                    String label = tag.label().getString().toLowerCase(java.util.Locale.ROOT);
                    if (tags.contains("tooltip.steveparty.tag." + tag.name().toLowerCase(java.util.Locale.ROOT)) && name.contains(label))
                        redundant.add(id.getPath() + " [" + label + "]");
                }
            }
        }
        context.assertTrue(tooLong.isEmpty(), "tooltips over 5 lines without Shift: " + tooLong);
        context.assertTrue(overBudget.size() * 10 <= items, "tooltips over 3 lines without Shift (at most 1 in 10): " + overBudget);
        context.assertTrue(crafting.isEmpty(), "crafting in tooltips (REI shows it): " + crafting);
        context.assertTrue(redundant.isEmpty(), "tags that repeat the name: " + redundant);
        context.complete();
    }
}
