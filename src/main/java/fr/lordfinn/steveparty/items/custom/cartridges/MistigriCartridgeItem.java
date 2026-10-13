package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.MistigriOutcomes;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.OutcomeListModule;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The Mistigri Cartridge: a token stopping on its tile meets the Mistigri, who leaps onto the space and rolls his giant
 * loaded die: it passes one of the cartridge's sentences (see MistigriTileBehavior and MistigriSentences). Its menu is
 * the list of those sentences, written by the players ({@link MistigriOutcomes}, {@link OutcomeListModule}). Its tile
 * is witch plum; a dye on it changes that.
 */
public class MistigriCartridgeItem extends CartridgeItem implements MobSpawnCartridge {
    /** Its tile's witch plum. */
    public static final int COLOR = 0x23202A;
    private static final List<CartridgeModule> MODULES = List.of(
            new OutcomeListModule("outcomes", MENU_KEY + "mistigri.outcomes").tipped());

    public MistigriCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public net.minecraft.entity.EntityType<?> spawnedMob() {
        return fr.lordfinn.steveparty.entities.ModEntities.MISTIGRI;
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_MISTIGRI;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        if (MistigriOutcomes.of(stack).drawCount() == 0) tips.warn(Text.translatable("tooltip.steveparty.mistigri_cartridge.none"));
    }
}
