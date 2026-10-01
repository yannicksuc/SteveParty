package fr.lordfinn.steveparty.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.steveparty.blocks.custom.HopSwitchBlock;
import fr.lordfinn.steveparty.entities.custom.MulaDances;
import fr.lordfinn.steveparty.entities.custom.MulaFood;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Player-visible texts come from the lang files, and both languages have every key. */
public class LangGameTests implements FabricGameTest {

    private static JsonObject lang(String code) {
        try (InputStream in = LangGameTests.class.getResourceAsStream("/assets/steveparty/lang/" + code + ".json")) {
            if (in == null) throw new AssertionError("no lang file " + code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Keys given to the texts that used to be hard-coded (wrench, token, plunger, Mula, commands, hop switch...). */
    private static final List<String> MOVED_TEXTS = List.of(
            "message.steveparty.wrench.bound", "message.steveparty.wrench.unbound",
            "message.steveparty.wrench.auto_unbound", "message.steveparty.wrench.cartridge_stored",
            "message.steveparty.wrench.no_cartridge",
            "item.steveparty.token.of", "tooltip.steveparty.token.summon", "message.steveparty.token.captured",
            "message.steveparty.token.summoned", "message.steveparty.token.summoned_on_trader",
            "message.steveparty.token.captured_from_trader",
            "message.steveparty.plunger.silenced", "message.steveparty.plunger.unsilenced",
            "message.steveparty.disconnected_player",
            "command.steveparty.move_token.not_found", "command.steveparty.move_token.moved",
            "command.steveparty.mula.ephemeride_started", "command.steveparty.mula.no_spawn_site",
            "command.steveparty.mula.nearest_site.came", "command.steveparty.mula.nearest_site.waiting",
            "command.steveparty.mula.unknown_animation",
            "message.steveparty.mula.feed", "message.steveparty.mula.feed.potion",
            "message.steveparty.mula.feed.potion_term",
            "message.steveparty.hopswitch.time.added", "message.steveparty.hopswitch.time.removed",
            "message.steveparty.hopswitch.duration.seconds", "message.steveparty.hopswitch.duration.minutes",
            "message.steveparty.hopswitch.duration.minutes_seconds", "message.steveparty.hopswitch.decimal",
            "scoreboard.steveparty.goal_pole", "scoreboard.steveparty.goal_pole_source");

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void englishAndFrenchHaveTheSameKeys(TestContext context) {
        JsonObject en = lang("en_us"), fr = lang("fr_fr");
        Set<String> missingInFr = new TreeSet<>(en.keySet());
        missingInFr.removeAll(fr.keySet());
        context.assertTrue(missingInFr.isEmpty(), "missing in fr_fr: " + missingInFr);
        for (String key : MOVED_TEXTS) {
            context.assertTrue(en.has(key) && fr.has(key), "key in both languages: " + key);
        }
        for (String id : MulaDances.IDS) {
            context.assertTrue(en.has("mula.dance." + id) && fr.has("mula.dance." + id), "dance name: " + id);
        }
        context.complete();
    }

    private static String key(Text text) {
        return text.getContent() instanceof TranslatableTextContent t ? t.getKey() : "literal " + text.getString();
    }

    /** The hop switch duration is spelled out through lang keys: half seconds, minutes, minutes and seconds. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hopSwitchDurationIsTranslated(TestContext context) {
        Text half = HopSwitchBlock.formatDuration(50); // 2.5 s
        context.assertEquals(key(half), "message.steveparty.hopswitch.duration.seconds", "seconds");
        Object arg = ((TranslatableTextContent) half.getContent()).getArgs()[0];
        context.assertTrue(arg instanceof Text t && key(t).equals("message.steveparty.hopswitch.decimal"),
                "2.5 uses the language's decimal separator: " + arg);
        context.assertEquals(key(HopSwitchBlock.formatDuration(1200)), "message.steveparty.hopswitch.duration.minutes", "1 min");
        Text mixed = HopSwitchBlock.formatDuration(2100); // 1 min 45 s
        context.assertEquals(key(mixed), "message.steveparty.hopswitch.duration.minutes_seconds", "1 min 45 s");
        Object[] args = ((TranslatableTextContent) mixed.getContent()).getArgs();
        context.assertTrue(args[0].equals(1) && ((Text) args[1]).getString().equals("45"), "1 min 45 s: " + List.of(args));
        context.complete();
    }

    /** The Mula's potion formula is a translated text: Strength II (1:30) gives 1 min x (2 x 2). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mulaPotionFormulaIsTranslated(TestContext context) {
        Text formula = MulaFood.potionFormula(List.of(new StatusEffectInstance(StatusEffects.STRENGTH, 1800, 1)));
        Text term = formula.getSiblings().getFirst();
        context.assertEquals(key(term), "message.steveparty.mula.feed.potion_term", "translated term");
        Object[] args = ((TranslatableTextContent) term.getContent()).getArgs();
        context.assertTrue(args[0].equals(1) && args[1].equals(2), "1 min, level 2: " + List.of(args));
        context.assertEquals(MulaFood.potionValue(List.of(new StatusEffectInstance(StatusEffects.STRENGTH, 1800, 1))), 4,
                "Strength II for 1:30 feeds 4");
        context.complete();
    }
}
