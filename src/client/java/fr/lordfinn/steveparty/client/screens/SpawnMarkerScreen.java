package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerSettings;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.entities.ModEntities;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * The menu of a Spawn Bubble (an empty hand on its plate): when its mob shows (only when a token lands on its space, or all
 * the party long), how high above the plate its bubble floats (-4 to +8 blocks, by half blocks; Shift: by blocks; the
 * wheel too) and which way its mob faces (a sixteenth of a turn per click). Each change is sent at once; the bubble
 * shows where the mob will be.
 */
public class SpawnMarkerScreen extends Screen {
    private static final int PANEL_W = 220, PAD = 12;
    private static final String K = "gui.steveparty.spawn_marker.";

    private final BlockPos pos;
    private boolean resident;
    /** Linked to star spaces only: its Mula shows while the star is there, not on landing. */
    private final boolean star;
    private int lift;
    /** The sixteenth of a turn its mob faces ({@link SpawnMarkerBlock#ROTATION}). */
    private int rotation;
    private PartyButton onLanding, always;
    private int panelTop, panelHeight, whenY, liftY, liftLabelHeight, turnY, turnLabelHeight;

    public SpawnMarkerScreen(BlockPos pos, SpawnMarkerBlockEntity marker) {
        super(Text.translatable(K + "title"));
        this.pos = pos.toImmutable();
        this.resident = marker.isResident();
        this.lift = marker.getLiftSteps();
        this.rotation = marker.getCachedState().get(SpawnMarkerBlock.ROTATION);
        this.star = !marker.getLinkedMobs().isEmpty()
                && marker.getLinkedMobs().stream().allMatch(type -> type == ModEntities.MULA_ENTITY);
    }

    @Override
    protected void init() {
        int inner = PANEL_W - 2 * PAD;
        // Measured in the current language: the labels wrap, the panel grows with them
        int whenHeight = UiText.height(textRenderer, Text.translatable(K + "when"), inner);
        liftLabelHeight = UiText.height(textRenderer, Text.translatable(K + "lift"), liftLabelWidth());
        int liftRow = Math.max(20, liftLabelHeight);
        turnLabelHeight = UiText.height(textRenderer, Text.translatable(K + "turn"), liftLabelWidth());
        int turnRow = Math.max(20, turnLabelHeight);
        panelHeight = 18 + whenHeight + 2 + 20 + 6 + liftRow + 6 + turnRow + PAD;
        panelTop = (height - panelHeight) / 2;
        int left = (width - PANEL_W) / 2 + PAD, right = (width + PANEL_W) / 2 - PAD;
        int half = (inner - 4) / 2;
        whenY = panelTop + 18;
        int y = whenY + whenHeight + 2;
        String whenShown = star ? "with_star" : "on_landing";
        onLanding = addDrawableChild(new PartyButton(left, y, half, 20, Text.translatable(K + whenShown), b -> setResident(false)));
        always = addDrawableChild(new PartyButton(left + half + 4, y, half, 20, Text.translatable(K + "resident"), b -> setResident(true)));
        onLanding.setTooltip(Tooltip.of(Text.translatable(K + whenShown + ".hint")));
        always.setTooltip(Tooltip.of(Text.translatable(K + (star ? "resident.star_hint" : "resident.hint"))));
        liftY = y + 20 + 6 + (liftRow - 20) / 2;
        addDrawableChild(new PartyButton(right - 70, liftY, 20, 20, Text.literal("-"), b -> step(-1)))
                .setTooltip(Tooltip.of(Text.translatable(K + "lift.hint")));
        addDrawableChild(new PartyButton(right - 20, liftY, 20, 20, Text.literal("+"), b -> step(1)))
                .setTooltip(Tooltip.of(Text.translatable(K + "lift.hint")));
        turnY = liftY + 20 + (liftRow - 20) / 2 + 6 + (turnRow - 20) / 2;
        addDrawableChild(new PartyButton(right - 70, turnY, 20, 20, Text.literal("↺"), b -> turn(-1)))
                .setTooltip(Tooltip.of(Text.translatable(K + "turn.left")));
        addDrawableChild(new PartyButton(right - 20, turnY, 20, 20, Text.literal("↻"), b -> turn(1)))
                .setTooltip(Tooltip.of(Text.translatable(K + "turn.right")));
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

    /** A sixteenth of a turn (22.5°), clockwise or not. */
    private void turn(int direction) {
        rotation = (rotation + direction) & 15;
        send();
    }

    private void send() {
        if (ClientPlayNetworking.canSend(SpawnMarkerSettings.ID)) ClientPlayNetworking.send(new SpawnMarkerSettings(pos, resident, lift, rotation));
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
        UiText.wrapped(context, textRenderer, Text.translatable(K + "turn"), left + PAD, turnY + 10 - turnLabelHeight / 2,
                liftLabelWidth(), PartyGui.TEXT_DARK, false);
        PartyGui.inset(context, centre - 14, turnY, 28, 20, 0xFFFFFFFF, false, false);
        Text facing = Text.translatable("message.steveparty.spawn_marker.facing." + SpawnMarkerBlock.compass(rotation));
        UiText.centered(context, textRenderer, facing.getString(), centre - 13, turnY + 6, 26, PartyGui.TEXT_DARK, false);
        for (var child : children()) {
            if (child instanceof PartyButton button) button.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
