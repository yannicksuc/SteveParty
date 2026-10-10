package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerSettings;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * The menu of a Spawn Marker (an empty hand on it): when its mob shows (only when a token lands on its space, or all
 * the party long) and how high above the marker it appears (-4 to +8 blocks, by half blocks; Shift: by blocks; the
 * wheel too). Each change is sent at once; the brush, the Wrench or the Explorer's Helmet show where the mob will be.
 */
public class SpawnMarkerScreen extends Screen {
    private static final int PANEL_W = 220, PAD = 12;
    private static final String K = "gui.steveparty.spawn_marker.";

    private final BlockPos pos;
    private boolean resident;
    private int lift;
    private PartyButton onLanding, always;
    private int panelTop, panelHeight, whenY, liftY, liftLabelHeight, hintY;

    public SpawnMarkerScreen(BlockPos pos, SpawnMarkerBlockEntity marker) {
        super(Text.translatable(K + "title"));
        this.pos = pos.toImmutable();
        this.resident = marker.isResident();
        this.lift = marker.getLiftSteps();
    }

    @Override
    protected void init() {
        int inner = PANEL_W - 2 * PAD;
        // Measured in the current language: the labels wrap, the panel grows with them
        int whenHeight = UiText.height(textRenderer, Text.translatable(K + "when"), inner);
        liftLabelHeight = UiText.height(textRenderer, Text.translatable(K + "lift"), liftLabelWidth());
        int liftRow = Math.max(20, liftLabelHeight);
        int hintHeight = UiText.height(textRenderer, Text.translatable(K + "preview"), inner);
        panelHeight = 18 + whenHeight + 2 + 20 + 6 + liftRow + 6 + hintHeight + PAD;
        panelTop = (height - panelHeight) / 2;
        int left = (width - PANEL_W) / 2 + PAD, right = (width + PANEL_W) / 2 - PAD;
        int half = (inner - 4) / 2;
        whenY = panelTop + 18;
        int y = whenY + whenHeight + 2;
        onLanding = addDrawableChild(new PartyButton(left, y, half, 20, Text.translatable(K + "on_landing"), b -> setResident(false)));
        always = addDrawableChild(new PartyButton(left + half + 4, y, half, 20, Text.translatable(K + "resident"), b -> setResident(true)));
        onLanding.setTooltip(Tooltip.of(Text.translatable(K + "on_landing.hint")));
        always.setTooltip(Tooltip.of(Text.translatable(K + "resident.hint")));
        liftY = y + 20 + 6 + (liftRow - 20) / 2;
        hintY = y + 20 + 6 + liftRow + 6;
        addDrawableChild(new PartyButton(right - 70, liftY, 20, 20, Text.literal("-"), b -> step(-1)))
                .setTooltip(Tooltip.of(Text.translatable(K + "lift.hint")));
        addDrawableChild(new PartyButton(right - 20, liftY, 20, 20, Text.literal("+"), b -> step(1)))
                .setTooltip(Tooltip.of(Text.translatable(K + "lift.hint")));
        refresh();
    }

    private void setResident(boolean resident) {
        this.resident = resident;
        send();
    }

    /** Half a block up or down (a block with Shift). */
    private void step(int direction) {
        lift = MathHelper.clamp(lift + direction * (hasShiftDown() ? 2 : 1), SpawnMarkerBlockEntity.MIN_LIFT, SpawnMarkerBlockEntity.MAX_LIFT);
        send();
    }

    private void send() {
        if (ClientPlayNetworking.canSend(SpawnMarkerSettings.ID)) ClientPlayNetworking.send(new SpawnMarkerSettings(pos, resident, lift));
        refresh();
    }

    private void refresh() {
        onLanding.setSelected(!resident);
        always.setSelected(resident);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0) {
            step(vertical > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void tick() {
        // The marker gone (broken, too far): nothing to set any more
        if (client == null || client.world == null || client.player == null
                || !(client.world.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity)
                || client.player.squaredDistanceTo(pos.toCenterPos()) > 64) close();
    }

    /** Room for the height label, left of its − value + buttons. */
    private int liftLabelWidth() {
        return PANEL_W - 2 * PAD - 70 - 4;
    }

    /** -4, -3.5... +8 blocks. */
    private static String lift(int steps) {
        String value = steps % 2 == 0 ? Integer.toString(steps / 2) : (steps / 2.0 + "");
        return steps > 0 ? "+" + value : value;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = (width - PANEL_W) / 2;
        PartyGui.panel(context, left, panelTop, PANEL_W, panelHeight, PartyGui.PANEL);
        PartyGui.titlePlate(context, textRenderer, width / 2, panelTop - 11, 0, title, PartyGui.FLAG_RED);
        int inner = PANEL_W - 2 * PAD;
        UiText.wrapped(context, textRenderer, Text.translatable(K + "when"), left + PAD, whenY, inner, PartyGui.TEXT_DARK, false);
        UiText.wrapped(context, textRenderer, Text.translatable(K + "lift"), left + PAD, liftY + 10 - liftLabelHeight / 2,
                liftLabelWidth(), PartyGui.TEXT_DARK, false);
        String value = lift(lift);
        int centre = (width + PANEL_W) / 2 - PAD - 35;
        PartyGui.inset(context, centre - 14, liftY, 28, 20, 0xFFFFFFFF, false, false);
        UiText.centered(context, textRenderer, value, centre - 13, liftY + 6, 26, PartyGui.TEXT_DARK, false);
        UiText.wrapped(context, textRenderer, Text.translatable(K + "preview"), left + PAD, hintY, inner, PartyGui.TEXT_SOFT, false, true);
        for (var child : children()) {
            if (child instanceof PartyButton button) button.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
