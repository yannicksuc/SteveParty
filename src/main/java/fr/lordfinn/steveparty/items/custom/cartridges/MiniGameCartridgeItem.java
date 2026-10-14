package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGameMode;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The Mini-game Cartridge: a pawn stopping on its space starts a mini-game right after its turn, drawn from the Party
 * Controller's catalogue as a mini-game card's, its teams made by the cartridge's mode ({@link MiniGameMode}): VS (the
 * player who landed against all the others), free for all, or the player's choice in the chat. Its tile is orange with
 * « VS »; a dye on it changes that.
 * <p>
 * Settings ({@link ModComponents#BOARD_CARTRIDGE_SETTINGS}): {@link #MODE} (0 VS, 1 free for all, 2 the player's
 * choice; VS by default).
 */
public class MiniGameCartridgeItem extends BoardRuleCartridgeItem {
    /** Its tile's orange. */
    public static final int COLOR = 0xF08A24;
    public static final String MODE = "mode";
    /** The modes of the menu, in the order of its choice. */
    public static final List<MiniGameMode> MODES = List.of(MiniGameMode.VS, MiniGameMode.FFA, MiniGameMode.CHOICE);

    private static final String K = MENU_KEY + "minigame.";
    private static final Setting MODE_SETTING = new Setting(MODE, 0, 0, MODES.size() - 1);
    private static final List<CartridgeModule> MODULES = List.of(
            description("minigame_cartridge", 2),
            MODE_SETTING.choice("mode", K + "mode", MODES.stream().map(mode -> ChoiceModule.Option.tipped(K + "mode." + mode.id())).toList()),
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

    public static MiniGameMode mode(ItemStack stack) {
        return MODES.get(MODE_SETTING.get(stack));
    }

    /** A cartridge in this mode (tests, commands). */
    public static ItemStack with(Item item, MiniGameMode mode) {
        ItemStack stack = new ItemStack(item);
        putSetting(stack, MODE, Math.max(0, MODES.indexOf(mode)));
        return stack;
    }

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        tips.state("tooltip.steveparty.minigame_cartridge.mode", Tooltips.setting(Text.translatable(K + "mode." + mode(stack).id())));
    }

    @Override
    protected void appendMore(ItemStack stack, Tooltips.More more) {
        more.note("tooltip.steveparty.minigame_cartridge.catalogue");
    }
}
