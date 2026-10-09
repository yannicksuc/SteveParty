package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGameText;
import fr.lordfinn.steveparty.minigame.RichText;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;

import java.util.Arrays;

/**
 * The page editor's text ({@link RichText}): read from the stored codes and written back, formatted by selection, its
 * characters counted without the codes.
 */
public class RichTextGameTests implements FabricGameTest {
    private static void check(TestContext context, boolean condition, String message) {
        if (!condition) throw new GameTestException(message);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void codesRoundTrip(TestContext context) {
        String[] samples = {"", "plain text", "&lBold&r and &oitalic&r.", "&cRed &lbold red&r plain", "a && b &&l", "&l&oboth&r",
                "line\n&aGreen\n&nunder&mstrike", "&LUPPER &Cred", "&zunknown & alone &"};
        for (String sample : samples) {
            RichText text = RichText.fromCodes(sample);
            String codes = text.toCodes();
            check(context, RichText.fromCodes(codes).equals(text), "Round trip changed " + sample + " -> " + codes);
            check(context, text.plain().equals(MiniGameText.strip(sample)), "Shown text of " + sample + ": " + text.plain() + " / " + MiniGameText.strip(sample));
            check(context, MiniGameText.parse(codes).getString().equals(text.plain()), "Parsed codes of " + sample + " show " + MiniGameText.parse(codes).getString());
        }
        // As few codes as possible; a colour ends the styles (as the markup reads them)
        check(context, RichText.fromCodes("&lHello&r world").toCodes().equals("&lHello&r world"), "bold then plain");
        check(context, !RichText.fromCodes("&lA&cB").styleAt(1).bold(), "a colour ends bold");
        check(context, RichText.fromCodes("&c&lA&cB").toCodes().equals("&c&lA&cB"), "bold red then red: " + RichText.fromCodes("&c&lA&cB").toCodes());
        check(context, RichText.fromCodes("a && b").plain().equals("a & b") && RichText.fromCodes("a && b").toCodes().equals("a && b"), "&& is a plain &");
        check(context, RichText.fromCodes("&LX").styleAt(0).bold(), "upper-case codes");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void selectionIsFormatted(TestContext context) {
        RichText text = RichText.fromCodes("Hello world");
        text.apply(0, 5, style -> style.withBold(true));
        check(context, text.toCodes().equals("&lHello&r world"), "bold selection: " + text.toCodes());
        text.apply(6, 11, style -> style.withColor('a'));
        check(context, text.toCodes().equals("&lHello&r &aworld"), "green selection: " + text.toCodes());
        // The toolbar's state: lit when the whole selection is so
        check(context, text.all(0, 5, RichText.Style::bold) && !text.all(0, 6, RichText.Style::bold), "bold state of the selection");
        // Typed after a bold word: bold
        text.insert(5, "!", text.styleFor(5));
        check(context, text.toCodes().equals("&lHello!&r &aworld"), "typed after bold: " + text.toCodes());
        text.apply(3, 9, style -> style.withItalic(true));
        check(context, RichText.fromCodes(text.toCodes()).equals(text), "italic across styles: " + text.toCodes());
        check(context, text.styleAt(3).bold() && text.styleAt(3).italic() && text.styleAt(8).italic() && text.styleAt(8).color() == 'a', "italic kept the others");
        // Clearing the formatting
        text.apply(0, text.length(), style -> RichText.Style.PLAIN);
        check(context, text.toCodes().equals("Hello! world"), "cleared: " + text.toCodes());
        // Words (double click, Ctrl + arrows)
        check(context, Arrays.equals(text.wordAt(8), new int[]{7, 12}) && text.wordLeft(12) == 7 && text.wordRight(0) == 5, "words");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shownCharactersAreCounted(TestContext context) {
        int max = MiniGamePageData.MAX_DESCRIPTION_LENGTH;
        check(context, RichText.fromCodes("&l&cAB&&&r").length() == 3, "codes are not counted");
        // The codes don't take the room of the characters
        String coloured = "&cx".repeat(max);
        String kept = MiniGamePageData.cleanDescription(coloured);
        check(context, MiniGameText.strip(kept).length() == max, "400 coloured characters kept: " + MiniGameText.strip(kept).length());
        // Beyond: cut at the characters shown, the formatting kept
        String cut = MiniGamePageData.cleanDescription("&l" + "x".repeat(max) + "yyy");
        check(context, MiniGameText.strip(cut).length() == max && cut.startsWith("&l") && !cut.contains("y"), "cut at 400: " + cut.length());
        String tooLong = MiniGamePageData.cleanDescription("&cx".repeat(1000));
        check(context, MiniGameText.strip(tooLong).length() == max && tooLong.length() <= MiniGamePageData.MAX_DESCRIPTION_STORED, "stored codes cut too");
        context.complete();
    }
}
