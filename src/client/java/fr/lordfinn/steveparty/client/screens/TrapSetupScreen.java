package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.blockentity.TrapMarkRenderer;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.components.TrapSetupComponent;
import fr.lordfinn.steveparty.items.custom.TrapPowerUpItem;
import fr.lordfinn.steveparty.payloads.custom.TrapSetupPayloads;
import fr.lordfinn.steveparty.powerups.effects.TrapKind;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
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
    private PartyButton minus, plus;
    private int panelTop, panelHeight, amountY, previewY;

    public TrapSetupScreen(Hand hand) {
        super(Text.translatable(K + "title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        kindButtons.clear();
        panelHeight = 18 + 12 + KIND_H + 10 + 20 + 8 + 2 * 10 + 10 + 20 + PAD;
        panelTop = (height - panelHeight) / 2;
        int left = (width - PANEL_W) / 2 + PAD;
        int y = panelTop + 18 + 12;
        for (TrapKind option : TrapKind.values()) {
            int x = left + option.ordinal() * (KIND_W + GAP);
            PartyButton button = addDrawableChild(new PartyButton(x, y, KIND_W, KIND_H, Text.translatable(K + "kind." + option.id()),
                    b -> choose(option)).content((context, font, cx, cy, color) ->
                    context.drawTexture(TrapMarkRenderer.icon(option), cx - 8, cy - 8, 0, 0, 16, 16, 16, 16)));
            button.setTooltip(Tooltip.of(Text.translatable(K + "kind." + option.id())));
            kindButtons.add(button);
        }
        amountY = y + KIND_H + 10;
        int right = (width + PANEL_W) / 2 - PAD;
        minus = addDrawableChild(new PartyButton(right - 70, amountY, 20, 20, Text.literal("-"), b -> step(-1)));
        plus = addDrawableChild(new PartyButton(right - 20, amountY, 20, 20, Text.literal("+"), b -> step(1)));
        previewY = amountY + 20 + 8;
        int buttonsY = previewY + 2 * 10 + 10;
        int half = (PANEL_W - 2 * PAD - GAP) / 2;
        addDrawableChild(new PartyButton(left, buttonsY, half, 20, Text.translatable(K + "cancel"), b -> close()));
        addDrawableChild(new PartyButton(left + half + GAP, buttonsY, half, 20, Text.translatable(K + "sign"), b -> sign()))
                .style(PartyButton.Style.PRIMARY);
        choose(kind);
    }

    private void choose(TrapKind option) {
        if (option != kind) amount = option.defaultAmount;
        kind = option;
        for (int i = 0; i < kindButtons.size(); i++) kindButtons.get(i).setSelected(i == option.ordinal());
        minus.visible = plus.visible = option.hasAmount();
    }

    /** One more or less (five with Shift). */
    private void step(int direction) {
        amount = kind.clamp(amount + direction * (hasShiftDown() ? 5 : 1));
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
        context.drawText(textRenderer, Text.translatable(K + "effect"), left + PAD, panelTop + 18, PartyGui.TEXT_DARK, false);
        if (kind.hasAmount()) {
            context.drawText(textRenderer, Text.translatable(K + "amount." + kind.id()), left + PAD, amountY + 6, PartyGui.TEXT_DARK, false);
            String value = Integer.toString(amount);
            int centre = (width + PANEL_W) / 2 - PAD - 35;
            PartyGui.inset(context, centre - 14, amountY, 28, 20, 0xFFFFFFFF, false, false);
            context.drawText(textRenderer, value, centre - textRenderer.getWidth(value) / 2, amountY + 6, PartyGui.TEXT_DARK, false);
        } else {
            Text name = Text.translatable(K + "kind." + kind.id());
            context.drawText(textRenderer, name, width / 2 - textRenderer.getWidth(name) / 2, amountY + 6, PartyGui.TEXT_DARK, false);
        }
        Text preview = Text.translatable(K + "preview", new TrapSetupComponent.Effect(kind, amount).describe());
        int y = previewY;
        for (OrderedText line : textRenderer.wrapLines(preview, PANEL_W - 2 * PAD)) {
            context.drawText(textRenderer, line, width / 2 - textRenderer.getWidth(line) / 2, y, PartyGui.TEXT_ERROR, false);
            y += 10;
        }
        Text warning = Text.translatable(K + "warning");
        context.drawText(textRenderer, warning, width / 2 - textRenderer.getWidth(warning) / 2, y + 2,
                PartyGui.TEXT_SOFT, false);
        for (var child : children()) {
            if (child instanceof PartyButton button) button.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
