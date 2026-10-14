package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGameMode;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.MiniGamePageRef;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.PageSlotModule;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Mini-game Cartridge: a pawn stopping on its space starts a mini-game right after its turn, drawn from the Party
 * Controller's catalogue as a mini-game card's (or always the page it keeps), its teams made by its format
 * ({@link MiniGameMode}): 1 v 1 (against an opponent the player who landed picks, or drawn), 1 against all, free for
 * all, the board's teams. Several formats on: the player who landed picks one in the chat. Its tile is orange with
 * « VS »; a dye on it changes that.
 * <p>
 * Settings ({@link ModComponents#BOARD_CARTRIDGE_SETTINGS}): {@link #FORMATS} (a bit per format of {@link #FORMAT_LIST},
 * 1 against all by default), {@link #OPPONENT} (0 picked, 1 drawn). Its page: {@link ModComponents#CARTRIDGE_MINI_GAME_PAGE}.
 */
public class MiniGameCartridgeItem extends BoardRuleCartridgeItem {
    /** Its tile's orange. */
    public static final int COLOR = 0xF08A24;
    public static final String FORMATS = "formats", OPPONENT = "opponent";
    /** The formats of the menu, in its order ({@link MiniGameMode#DUEL}: the 1 v 1, its opponent picked or drawn). */
    public static final List<MiniGameMode> FORMAT_LIST = List.of(MiniGameMode.DUEL, MiniGameMode.VS, MiniGameMode.FFA, MiniGameMode.TEAMS);

    private static final String K = MENU_KEY + "minigame.";
    private static final Setting FORMATS_SETTING = new Setting(FORMATS, 1 << FORMAT_LIST.indexOf(MiniGameMode.VS), 1, (1 << FORMAT_LIST.size()) - 1);
    private static final Setting OPPONENT_SETTING = new Setting(OPPONENT, 0, 0, 1);
    private static final List<CartridgeModule> MODULES = List.of(
            description("minigame_cartridge", 2),
            new ChoiceModule("formats", K + "formats", FORMAT_LIST.stream().map(mode -> ChoiceModule.Option.tipped(K + "format." + mode.id())).toList(),
                    FORMATS_SETTING::get, (edit, value) -> FORMATS_SETTING.put(edit.stack(), value)).toggles().perRow(2).tipped(),
            OPPONENT_SETTING.choice("opponent", K + "opponent", List.of(ChoiceModule.Option.tipped(K + "opponent.picked"),
                    ChoiceModule.Option.tipped(K + "opponent.random")), MiniGameCartridgeItem::playsDuel),
            new PageSlotModule("page", K + "page", ModComponents.CARTRIDGE_MINI_GAME_PAGE, K + "page.hint").tipped(K + "page.hint"),
            colorModule(COLOR));

    public MiniGameCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_MINIGAME;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    /** The formats on, in the menu's order, the 1 v 1 as {@link MiniGameMode#DUEL} (picked) or {@link MiniGameMode#DUEL_RANDOM}. */
    public static List<MiniGameMode> formats(ItemStack stack) {
        int bits = FORMATS_SETTING.get(stack);
        List<MiniGameMode> formats = new ArrayList<>();
        for (int i = 0; i < FORMAT_LIST.size(); i++) {
            if ((bits >> i & 1) == 0) continue;
            MiniGameMode mode = FORMAT_LIST.get(i);
            formats.add(mode == MiniGameMode.DUEL && OPPONENT_SETTING.get(stack) == 1 ? MiniGameMode.DUEL_RANDOM : mode);
        }
        return formats;
    }

    /** Its one format, or {@link MiniGameMode#CHOICE} when several are on. */
    public static MiniGameMode mode(ItemStack stack) {
        List<MiniGameMode> formats = formats(stack);
        return formats.size() == 1 ? formats.getFirst() : MiniGameMode.CHOICE;
    }

    private static boolean playsDuel(ItemStack stack) {
        return (FORMATS_SETTING.get(stack) & 1 << FORMAT_LIST.indexOf(MiniGameMode.DUEL)) != 0;
    }

    /** The page it always plays, null for the catalogue. */
    public static @Nullable UUID page(ItemStack stack) {
        MiniGamePageRef ref = stack.get(ModComponents.CARTRIDGE_MINI_GAME_PAGE);
        return ref == null ? null : ref.id();
    }

    /** A cartridge with these formats (tests, commands); {@link MiniGameMode#DUEL_RANDOM}: the 1 v 1 drawn. */
    public static ItemStack with(Item item, MiniGameMode... formats) {
        ItemStack stack = new ItemStack(item);
        int bits = 0;
        for (MiniGameMode mode : formats) {
            if (mode == MiniGameMode.DUEL_RANDOM) putSetting(stack, OPPONENT, 1);
            int index = FORMAT_LIST.indexOf(mode == MiniGameMode.DUEL_RANDOM ? MiniGameMode.DUEL : mode);
            if (index >= 0) bits |= 1 << index;
        }
        if (bits != 0) putSetting(stack, FORMATS, bits);
        return stack;
    }

    /** « 1 contre tous », or « au choix : 1 contre 1, Chacun ». */
    public static MutableText formatsText(ItemStack stack) {
        List<MiniGameMode> formats = formats(stack);
        if (formats.size() == 1) return formatText(formats.getFirst());
        MutableText list = Text.empty();
        for (int i = 0; i < formats.size(); i++) list.append(i == 0 ? Text.empty() : Text.literal(", ")).append(formatText(formats.get(i)));
        return Text.translatable(K + "format.choice", list);
    }

    /** A format's name: « 1 contre 1 » (and « au hasard » for a drawn opponent), « Chacun »... */
    public static MutableText formatText(MiniGameMode mode) {
        if (mode == MiniGameMode.DUEL_RANDOM) return Text.translatable(K + "format.duel_random");
        return Text.translatable(K + "format." + mode.id());
    }

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        tips.state("tooltip.steveparty.minigame_cartridge.formats", Tooltips.setting(formatsText(stack)));
        MiniGamePageRef page = stack.get(ModComponents.CARTRIDGE_MINI_GAME_PAGE);
        if (page != null && !page.title().isEmpty()) tips.state("tooltip.steveparty.minigame_cartridge.page", Tooltips.setting(Text.literal(page.title())));
    }

    @Override
    protected void appendMore(ItemStack stack, Tooltips.More more) {
        more.note("tooltip.steveparty.minigame_cartridge.catalogue");
    }
}
