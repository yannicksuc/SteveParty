package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/**
 * Départ: where a token starts the party; the token set on it is bound to it, its owner's head shown (see
 * StartTileBehavior). Its menu: what it does, who is bound to it, and its tile's colour.
 */
public class StartCartridgeItem extends CartridgeItem {
    /** Its label's colour (the item's). */
    public static final int LABEL_COLOR = 0xFCFAB1;
    private static final String K = MENU_KEY + "start.";
    private static final List<CartridgeModule> MODULES = List.of(
            description("tile_behavior_start", 2),
            new InfoModule("binding", K + "binding", 2, StartCartridgeItem::binding),
            colorModule(PLAIN_COLOR));

    public StartCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_START;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int menuColor(ItemStack stack) {
        return LABEL_COLOR;
    }

    /** Its player (the owner of the bound token) and whether a token is bound. */
    private static List<InfoModule.Line> binding(InfoModule.Context context) {
        String owner = context.stack().get(ModComponents.TB_START_OWNER);
        Text player;
        if (owner == null) {
            player = Text.translatable(K + "no_owner");
        } else {
            PlayerEntity online = null;
            try {
                online = context.world().getPlayerByUuid(UUID.fromString(owner));
            } catch (IllegalArgumentException ignored) {
                // a damaged owner: shown as absent
            }
            player = Text.translatable(K + "owner", online != null ? online.getName() : Text.translatable(K + "owner.absent"));
        }
        boolean token = context.stack().get(ModComponents.TB_START_BOUND_ENTITY) != null;
        return List.of(new InfoModule.Line(player, owner == null ? InfoModule.Tone.SOFT : InfoModule.Tone.NORMAL),
                new InfoModule.Line(Text.translatable(token ? K + "token" : K + "no_token"),
                        token ? InfoModule.Tone.GOOD : InfoModule.Tone.SOFT));
    }
}
