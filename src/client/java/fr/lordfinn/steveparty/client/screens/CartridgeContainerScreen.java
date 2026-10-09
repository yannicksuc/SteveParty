package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.board.Pipette;
import fr.lordfinn.steveparty.client.gui.HandCursor;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.payloads.custom.PipettePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.CartridgeContainerScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The screens of the cartridge blocks. Those of the board spaces and routers ({@link #hasPipette}) have the
 * <b>pipette</b> (see {@link Pipette}): its button turns it on (the cursor becomes a pipette), the first cartridge
 * clicked is copied (its slot outlined), each next one gets its destinations; right click, Escape or the button again:
 * off. While on, a box by the cursor shows the destinations copied.
 */
public abstract class CartridgeContainerScreen<T extends CartridgeContainerScreenHandler> extends HandledScreen<T> {
    private static final Identifier PIPETTE_ICON = Steveparty.id("textures/gui/pipette.png");
    private static final int PIPETTE_BUTTON = 18;
    /** Coordinates listed in the box at most. */
    private static final int SHOWN_LINKS = 6;
    private static final int COPY_COLOR = 0xFF4CFF4C;

    private boolean pipetteOn;
    /** The slot copied (-1: none yet) and its destinations (as the client sees them). */
    private int pipetteSource = -1;
    private @Nullable List<BlockPos> pipetteLinks;
    private ItemStack pipetteCartridge = ItemStack.EMPTY;

    public CartridgeContainerScreen(T handler, PlayerInventory inventory, Text title, int backgroundHeight) {
        super(handler, inventory, title);
        this.backgroundHeight = backgroundHeight;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        context.drawTexture(getTexture(), x, y, 0f, 0f, backgroundWidth, backgroundHeight, 256, 256);
    }

    public abstract Identifier getTexture();

    @Override
    protected void init() {
        super.init();
        this.playerInventoryTitleY = this.backgroundHeight - 93;
    }

    // ---------------------------------------------------------------- the pipette

    /** Whether this screen has the pipette (board spaces and routers). */
    protected boolean hasPipette() {
        return false;
    }

    /** The right edge of the part the pipette button sits in, top right (the whole interface by default). */
    protected int pipetteRight() {
        return x + backgroundWidth;
    }

    protected int pipetteX() {
        return pipetteRight() - PIPETTE_BUTTON - 4;
    }

    protected int pipetteY() {
        return y + 4;
    }

    private boolean overPipetteButton(double mouseX, double mouseY) {
        return hasPipette() && HitArea.contains(mouseX, mouseY, pipetteX(), pipetteY(), PIPETTE_BUTTON, PIPETTE_BUTTON);
    }

    public boolean pipetteOn() {
        return pipetteOn;
    }

    private void setPipette(boolean on) {
        pipetteOn = on;
        pipetteSource = -1;
        pipetteLinks = null;
        pipetteCartridge = ItemStack.EMPTY;
        HandCursor.pipette(on);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overPipetteButton(mouseX, mouseY) && button == 0) {
            setPipette(!pipetteOn);
            return true;
        }
        if (!pipetteOn) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 1) {
            setPipette(false);
            return true;
        }
        if (button == 0) pipetteClick(focusedSlot);
        return true; // nothing else happens while the pipette is on
    }

    private void pipetteClick(@Nullable Slot slot) {
        if (slot == null || !Pipette.isItemSlot(handler, slot.id) || client == null || client.player == null) return;
        ItemStack stack = slot.getStack();
        if (pipetteSource < 0 || slot.id == pipetteSource) {
            List<BlockPos> links = Pipette.copyOf(stack);
            if (links == null) {
                client.player.sendMessage(Text.translatable("message.steveparty.pipette.no_source"), true);
                return;
            }
            pipetteSource = slot.id;
            pipetteLinks = links;
            pipetteCartridge = Pipette.cartridgeOf(stack).copy();
            client.player.sendMessage(Text.translatable("message.steveparty.pipette.copied", pipetteCartridge.getName(), links.size()), true);
            return;
        }
        if (Pipette.pasted(stack, pipetteLinks, client.player.getWorld()) == null) {
            client.player.sendMessage(Text.translatable("message.steveparty.pipette.no_target"), true);
            return;
        }
        ClientPlayNetworking.send(new PipettePayload(handler.syncId, pipetteSource, slot.id));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return pipetteOn || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return pipetteOn || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pipetteOn && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            setPipette(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The pipette's box replaces the item tooltips while it is on. */
    @Override
    protected void drawMouseoverTooltip(DrawContext context, int x, int y) {
        if (!pipetteOn) super.drawMouseoverTooltip(context, x, y);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (!hasPipette()) return;
        drawPipetteButton(context, mouseX, mouseY);
        if (pipetteOn) {
            drawPipetteSource(context);
            drawPipetteBox(context, mouseX, mouseY);
        } else if (overPipetteButton(mouseX, mouseY)) {
            context.drawTooltip(textRenderer, List.of(Text.translatable("gui.steveparty.pipette"),
                    Text.translatable("gui.steveparty.pipette.hint").formatted(Formatting.GRAY)), mouseX, mouseY);
        }
    }

    private void drawPipetteButton(DrawContext context, int mouseX, int mouseY) {
        int bx = pipetteX(), by = pipetteY();
        boolean hover = overPipetteButton(mouseX, mouseY);
        // A button of the mod (PartyGui): gold and pushed in while the pipette is on
        PartyGui.Theme theme = pipetteOn ? PartyGui.BUTTON_SELECTED : hover ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON;
        PartyGui.button(context, bx, by, PIPETTE_BUTTON, PIPETTE_BUTTON, theme, pipetteOn);
        if (hover) context.drawBorder(bx, by, PIPETTE_BUTTON, PIPETTE_BUTTON, 0xFFFFFFFF);
        int push = pipetteOn ? 1 : 0;
        context.drawTexture(PIPETTE_ICON, bx + 1 + push, by + 1 + push, 0, 0, 16, 16, 16, 16);
    }

    /** The slot copied: outlined in the link colour. */
    private void drawPipetteSource(DrawContext context) {
        if (pipetteSource < 0 || pipetteSource >= handler.slots.size()) return;
        Slot slot = handler.slots.get(pipetteSource);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        context.drawBorder(x + slot.x - 1, y + slot.y - 1, 18, 18, COPY_COLOR);
        context.drawBorder(x + slot.x - 2, y + slot.y - 2, 20, 20, COPY_COLOR);
        context.getMatrices().pop();
    }

    /** By the cursor: what is copied (the cartridge and its destinations), or what to click first. */
    private void drawPipetteBox(DrawContext context, int mouseX, int mouseY) {
        List<Text> lines = new ArrayList<>();
        if (pipetteLinks == null) {
            lines.add(Text.translatable("gui.steveparty.pipette.pick").formatted(Formatting.YELLOW));
        } else {
            lines.add(Text.translatable("gui.steveparty.pipette.copy", pipetteCartridge.getName()).formatted(Formatting.GREEN));
            lines.add(Text.translatable("gui.steveparty.pipette.count", pipetteLinks.size()).formatted(Formatting.WHITE));
            for (int i = 0; i < Math.min(SHOWN_LINKS, pipetteLinks.size()); i++) {
                lines.add(Text.literal("  → ").append(BoardText.pos(pipetteLinks.get(i))).formatted(Formatting.GRAY));
            }
            if (pipetteLinks.size() > SHOWN_LINKS) {
                lines.add(Text.translatable("gui.steveparty.pipette.more", pipetteLinks.size() - SHOWN_LINKS).formatted(Formatting.DARK_GRAY));
            }
            lines.add(Text.translatable("gui.steveparty.pipette.paste").formatted(Formatting.YELLOW));
        }
        lines.add(Text.translatable("gui.steveparty.pipette.exit").formatted(Formatting.DARK_GRAY));
        context.drawTooltip(textRenderer, lines, mouseX + 8, mouseY + 8);
    }

    @Override
    public void removed() {
        super.removed();
        pipetteOn = false;
        HandCursor.reset();
    }
}
