package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PiggyBankBlockEntity.Target;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.screen_handlers.custom.PiggyBankScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static fr.lordfinn.steveparty.screen_handlers.custom.PiggyBankScreenHandler.*;

/**
 * The Piggy Bank's menu: on its pink tab, the Inventory Cartridge slot and a button per target, each its icon (the
 * dice: the player of the turn, the gold podium: the winners, the party: everyone, the compass: the nearest); the one
 * chosen is gold and pushed in. Its name on hover only. Linked to no party (it does nothing then): the Tile Linker
 * Brush crossed out in red after the target buttons, what it means on hover. On the right, the cartridge's menu
 * (what is given or taken).
 */
public class PiggyBankScreen extends CartridgeContainerScreen<PiggyBankScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/piggy_bank.png");
    private static final int TITLE_COLOR = 0x404040;

    public PiggyBankScreen(PiggyBankScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, 136);
    }

    /** Its empty slot shows the Inventory Cartridge it takes. */
    @Override
    protected ItemStack emptySlotGhost() {
        return GHOST;
    }

    private static final ItemStack GHOST = new ItemStack(ModItems.INVENTORY_CARTRIDGE);

    /** Under its slot's pink. */
    @Override
    protected int emptySlotVeil() {
        return 0x99E2769C;
    }

    @Override
    public Identifier getTexture() {
        return TEXTURE;
    }

    /** The pink tab's right edge (its outline is at 167). */
    @Override
    protected int wireFromX() {
        return 168;
    }

    /** The crossed brush (unlinked), on the button row 4 px after the last button (its icon's row), from the menu's corner. */
    private static final int NOT_LINKED_X = BUTTONS_X + 3 * BUTTON_STEP + BUTTON + 4, NOT_LINKED_Y = BUTTONS_Y + 1;
    private static final ItemStack BRUSH = new ItemStack(ModItems.TILE_LINKER_BRUSH);
    private static final String NOT_LINKED_KEY = "gui.steveparty.piggy_bank.not_linked";

    private boolean notLinkedHovered(double mouseX, double mouseY) {
        return !handler.isLinked() && HitArea.contains(mouseX, mouseY, x + NOT_LINKED_X, y + NOT_LINKED_Y, 16, 16);
    }

    private int buttonX(int index) {
        return x + BUTTONS_X + index * BUTTON_STEP;
    }

    private int hovered(double mouseX, double mouseY) {
        for (int i = 0; i < Target.values().length; i++) {
            if (HitArea.contains(mouseX, mouseY, buttonX(i), y + BUTTONS_Y, BUTTON, BUTTON)) return i;
        }
        return -1;
    }

    /** The pink tab says what it is: only the inventory's title is written. */
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        UiText.line(context, textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY,
                titlesWidth() - 8 - playerInventoryTitleX, TITLE_COLOR, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int hover = hovered(mouseX, mouseY);
        Target chosen = handler.getTarget();
        for (Target target : Target.values()) {
            int i = target.ordinal(), bx = buttonX(i), by = y + BUTTONS_Y;
            boolean on = target == chosen;
            PartyGui.Theme theme = on ? PartyGui.BUTTON_SELECTED : hover == i ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON;
            PartyGui.button(context, bx, by, BUTTON, BUTTON, theme, on);
            if (hover == i) context.drawBorder(bx, by, BUTTON, BUTTON, 0xFFFFFFFF);
            int push = on ? 1 : 0;
            context.drawItem(target.getIcon(), bx + 1 + push, by + 1 + push);
        }
        if (!handler.isLinked()) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            context.drawItem(BRUSH, x + NOT_LINKED_X, y + NOT_LINKED_Y);
            context.getMatrices().translate(0, 0, 200);
            ConsolePaint.notLinked(context, x + NOT_LINKED_X + 16 - ConsolePaint.NOT_LINKED.length, y + NOT_LINKED_Y + 16 - ConsolePaint.NOT_LINKED.length);
            context.getMatrices().pop();
        }
        if (notLinkedHovered(mouseX, mouseY)) context.drawTooltip(textRenderer, Text.translatable(NOT_LINKED_KEY), mouseX, mouseY);
        else if (hover >= 0) context.drawTooltip(textRenderer, Target.byId(hover).getText(), mouseX, mouseY);
        else drawTooltips(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int hover = hovered(mouseX, mouseY);
        if (button == 0 && hover >= 0 && client != null && client.interactionManager != null) {
            client.interactionManager.clickButton(handler.syncId, hover);
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.8F + 0.1F * hover));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
