package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.blockentity.TrapMarkRenderer;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.components.TrapSetupComponent;
import fr.lordfinn.steveparty.items.custom.TrapPowerUpItem;
import fr.lordfinn.steveparty.payloads.custom.TrapSetupPayloads;
import fr.lordfinn.steveparty.powerups.effects.TrapKind;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

/**
 * The setup of an unsigned Trap, like a book and quill (opened by a sneak right-click, see TrapPowerUpItem): what it
 * will do (steal coins, send back, lose the next turn, steal an item), how many coins or spaces, then « Sign »: the
 * server signs the Trap held for good ({@link TrapSetupPayloads.Sign}). « Cancel » or Escape: it stays unsigned.
 */
public class TrapSetupScreen extends Screen {
    private static final int PANEL_W = 236, PAD = 12, KIND_W = 47, KIND_H = 30, GAP = 4;
    private static final String K = "gui.steveparty.trap_setup.";

    private final Hand hand;
    private TrapKind kind = TrapSetupComponent.DEFAULT.kind();
    private int amount = TrapSetupComponent.DEFAULT.amount();
    private final List<PartyButton> kindButtons = new ArrayList<>();
    private PartyButton minus, plus, cancel, signButton;
    private int panelTop, panelHeight, kindY, amountY, rowLabelHeight, previewY, previewHeight;

    public TrapSetupScreen(Hand hand) {
        super(Text.translatable(K + "title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        kindButtons.clear();
        // Placed by layout(): the panel grows with its texts, wrapped in the current language
        int left = (width - PANEL_W) / 2 + PAD;
        for (TrapKind option : TrapKind.values()) {
            int x = left + option.ordinal() * (KIND_W + GAP);
            PartyButton button = addDrawableChild(new PartyButton(x, 0, KIND_W, KIND_H, Text.translatable(K + "kind." + option.id()),
                    b -> choose(option)).content((context, font, cx, cy, color) ->
                    context.drawTexture(TrapMarkRenderer.icon(option), cx - 8, cy - 8, 0, 0, 16, 16, 16, 16)));
            button.setTooltip(Tooltip.of(Text.translatable(K + "kind." + option.id())));
            kindButtons.add(button);
        }
        int right = (width + PANEL_W) / 2 - PAD;
        minus = addDrawableChild(new PartyButton(right - 70, 0, 20, 20, Text.literal("-"), b -> step(-1)));
        plus = addDrawableChild(new PartyButton(right - 20, 0, 20, 20, Text.literal("+"), b -> step(1)));
        int half = (PANEL_W - 2 * PAD - GAP) / 2;
        cancel = addDrawableChild(new PartyButton(left, 0, half, 20, Text.translatable(K + "cancel"), b -> close()));
        signButton = addDrawableChild(new PartyButton(left + half + GAP, 0, half, 20, Text.translatable(K + "sign"), b -> sign()))
                .style(PartyButton.Style.PRIMARY);
        choose(kind);
    }

    /**
     * The panel's height and rows, measured in the current language for the kind and amount chosen (they change the
     * amount's label and the preview): the effect label, the kinds, the amount's row, the preview and the warning,
     * the buttons.
     */
    private void layout() {
        int inner = PANEL_W - 2 * PAD;
        int effectHeight = UiText.height(textRenderer, Text.translatable(K + "effect"), inner);
        rowLabelHeight = UiText.height(textRenderer, rowLabel(), kind.hasAmount() ? amountLabelWidth() : inner);
        int row = Math.max(20, rowLabelHeight);
        previewHeight = UiText.height(textRenderer, preview(), inner);
        int below = Math.max(2 * 10 + 10, previewHeight + UiText.height(textRenderer, Text.translatable(K + "warning"), inner));
        panelHeight = 18 + effectHeight + 2 + KIND_H + 10 + row + 8 + below + 20 + PAD;
        panelTop = (height - panelHeight) / 2;
        kindY = panelTop + 18 + effectHeight + 2;
        for (PartyButton button : kindButtons) button.setY(kindY);
        int rowY = kindY + KIND_H + 10;
        amountY = rowY + (row - 20) / 2;
        minus.setY(amountY);
        plus.setY(amountY);
        previewY = rowY + row + 8;
        cancel.setY(previewY + below);
        signButton.setY(previewY + below);
    }

    /** Room for the amount's label, left of its − value + buttons. */
    private static int amountLabelWidth() {
        return PANEL_W - 2 * PAD - 70 - 4;
    }

    /** The amount's label, or the kind's name when it has no amount. */
    private Text rowLabel() {
        return Text.translatable(K + (kind.hasAmount() ? "amount." : "kind.") + kind.id());
    }

    private Text preview() {
        return Text.translatable(K + "preview", new TrapSetupComponent.Effect(kind, amount).describe());
    }

    private void choose(TrapKind option) {
        if (option != kind) amount = option.defaultAmount;
        kind = option;
        for (int i = 0; i < kindButtons.size(); i++) kindButtons.get(i).setSelected(i == option.ordinal());
        minus.visible = plus.visible = option.hasAmount();
        layout();
    }

    /** One more or less (five with Shift). */
    private void step(int direction) {
        amount = kind.clamp(amount + direction * (hasShiftDown() ? 5 : 1));
        layout();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (kind.hasAmount() && vertical != 0) {
            step(vertical > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private void sign() {
        ClientPlayNetworking.send(new TrapSetupPayloads.Sign(hand, kind.ordinal(), amount));
        close();
    }

    @Override
    public void tick() {
        // The Trap left the hand (or was signed meanwhile): nothing to set up any more
        ItemStack held = client == null || client.player == null ? ItemStack.EMPTY : client.player.getStackInHand(hand);
        if (!(held.getItem() instanceof TrapPowerUpItem) || TrapSetupComponent.isSigned(held)) close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = (width - PANEL_W) / 2;
        PartyGui.panel(context, left, panelTop, PANEL_W, panelHeight, PartyGui.PANEL);
        PartyGui.titlePlate(context, textRenderer, width / 2, panelTop - 11, 0, title, PartyGui.FLAG_RED);
        int inner = PANEL_W - 2 * PAD;
        UiText.wrapped(context, textRenderer, Text.translatable(K + "effect"), left + PAD, panelTop + 18, inner, PartyGui.TEXT_DARK, false);
        // The amount's row: its label (or the kind's name, centred) in the middle of the row
        int labelY = amountY + 6 - (rowLabelHeight - UiText.LINE_H) / 2;
        if (kind.hasAmount()) {
            UiText.wrapped(context, textRenderer, rowLabel(), left + PAD, labelY, amountLabelWidth(), PartyGui.TEXT_DARK, false);
            String value = Integer.toString(amount);
            int centre = (width + PANEL_W) / 2 - PAD - 35;
            PartyGui.inset(context, centre - 14, amountY, 28, 20, 0xFFFFFFFF, false, false);
            UiText.centered(context, textRenderer, value, centre - 13, amountY + 6, 26, PartyGui.TEXT_DARK, false);
        } else {
            UiText.wrapped(context, textRenderer, rowLabel(), left + PAD, labelY, inner, PartyGui.TEXT_DARK, false, true);
        }
        UiText.wrapped(context, textRenderer, preview(), left + PAD, previewY, inner, PartyGui.TEXT_ERROR, false, true);
        UiText.wrapped(context, textRenderer, Text.translatable(K + "warning"), left + PAD, previewY + previewHeight + 2, inner,
                PartyGui.TEXT_SOFT, false, true);
        for (var child : children()) {
            if (child instanceof PartyButton button) button.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
