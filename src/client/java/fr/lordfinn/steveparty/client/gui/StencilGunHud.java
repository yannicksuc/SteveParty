package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.StencilGunSelection;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.payloads.custom.StencilGunScrollPayload;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Stencil gun controls on the client: sneak + mouse wheel cycles the selected stencil or colour, the mode key
 * (G by default) switches which one the wheel cycles. A small HUD above the hotbar shows both, the one the wheel
 * changes being framed.
 */
public final class StencilGunHud {
    private static final KeyBinding MODE_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.steveparty.stencil_gun_mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.steveparty"));
    private static final int PIXEL = 1;
    private static final int BOX = 16 * PIXEL + 4;
    /** The key hint is only a reminder: drawn see-through. */
    private static final int HINT_COLOR = 0x88DDDDDD;
    private static final int ACTIVE = 0xFFFFD83D;
    private static final int INACTIVE = 0xFF555555;

    /** True: the wheel picks the colour, false: the stencil. */
    private static boolean colorMode = false;

    /** What the HUD shows of a gun, worked out again only when its contents or selection change. */
    private record Shown(InventoryComponent contentsComponent, StencilGunSelection selectionComponent, List<ItemStack> contents,
                         StencilGunSelection selection, StencilGunItem.Load load) {
    }

    private static Shown shown;

    private StencilGunHud() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (MODE_KEY.wasPressed()) {
                if (client.player != null && isHoldingGun(client)) {
                    colorMode = !colorMode;
                    client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.6F));
                }
            }
        });
        HudRenderCallback.EVENT.register(StencilGunHud::render);
    }

    private static boolean isHoldingGun(MinecraftClient client) {
        return client.player != null && client.player.getMainHandStack().getItem() instanceof StencilGunItem;
    }

    /**
     * Mouse wheel hook: sneaking with a stencil gun in the main hand, the wheel cycles the gun instead of the hotbar.
     *
     * @return true if the scroll was used
     */
    public static boolean onScroll(double vertical) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.player == null || !client.player.isSneaking() || !isHoldingGun(client)) return false;
        if (vertical == 0) return true;
        int direction = vertical > 0 ? -1 : 1;
        ClientPlayNetworking.send(new StencilGunScrollPayload(colorMode, direction));
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.9F));
        return true;
    }

    /** Item components are immutable: the same component instances mean the same contents and selection. */
    private static Shown shown(ItemStack gun) {
        InventoryComponent contentsComponent = gun.get(ModComponents.STENCIL_GUN_CONTENTS);
        StencilGunSelection selectionComponent = gun.get(ModComponents.STENCIL_GUN_SELECTION);
        Shown last = shown;
        if (last != null && last.contentsComponent() == contentsComponent && last.selectionComponent() == selectionComponent) return last;
        List<ItemStack> contents = StencilGunItem.contents(gun);
        StencilGunSelection selection = StencilGunItem.validSelection(contents, StencilGunItem.selection(gun));
        StencilGunItem.Load load = StencilGunItem.selectedLoad(gun);
        shown = new Shown(contentsComponent, selectionComponent, contents, selection, load);
        return shown;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.currentScreen != null || !isHoldingGun(client)) return;
        Shown shown = shown(client.player.getMainHandStack());
        List<ItemStack> contents = shown.contents();
        StencilGunSelection selection = shown.selection();
        StencilGunItem.Load load = shown.load();

        int width = context.getScaledWindowWidth();
        // Right above the hotbar, or above the health / hunger rows when they are shown (survival, adventure)
        boolean statusBars = client.interactionManager != null && client.interactionManager.hasStatusBars();
        int y = context.getScaledWindowHeight() - (statusBars ? 50 : 26) - BOX;
        int stencilX = width / 2 - BOX - 4;
        int colorX = width / 2 + 4;

        // Stencil
        context.fill(stencilX, y, stencilX + BOX, y + BOX, 0xAA000000);
        context.drawBorder(stencilX, y, BOX, BOX, colorMode ? INACTIVE : ACTIVE);
        if (load.shape() != null) {
            int paint = load.color() != null ? 0xFF000000 | load.color().getEntityColor() : 0xFF8A8A8A;
            byte[] shape = load.shape();
            for (int px = 0; px < 16; px++) {
                for (int py = 0; py < 16; py++) {
                    if (!StencilShape.get(shape, px, py)) continue;
                    int sx = stencilX + 2 + px * PIXEL, sy = y + 2 + py * PIXEL;
                    context.fill(sx, sy, sx + PIXEL, sy + PIXEL, paint);
                }
            }
        }

        // Colour
        context.fill(colorX, y, colorX + BOX, y + BOX, 0xAA000000);
        context.drawBorder(colorX, y, BOX, BOX, colorMode ? ACTIVE : INACTIVE);
        ItemStack dye = selection.dye() == StencilGunSelection.ENGRAVE ? ItemStack.EMPTY : contents.get(StencilGunItem.STENCIL_SLOTS + selection.dye());
        if (dye.getItem() instanceof DyeItem) {
            context.getMatrices().push();
            context.getMatrices().translate(colorX + 2, y + 2, 0);
            context.drawItem(dye, 0, 0);
            context.getMatrices().pop();
            context.drawStackOverlay(client.textRenderer, dye, colorX + 2, y + 2);
        } else {
            Text engrave = Text.translatable("hud.steveparty.stencil_gun.engrave");
            context.getMatrices().push();
            context.getMatrices().translate(colorX + BOX / 2F, y + BOX / 2F - 2, 0);
            context.getMatrices().scale(0.5F, 0.5F, 1);
            context.drawCenteredTextWithShadow(client.textRenderer, engrave, 0, 0, 0xFFBBBBBB);
            context.getMatrices().pop();
        }

        // Hint (no stencil name: the preview says it)
        Text hint = Text.translatable(colorMode ? "hud.steveparty.stencil_gun.hint_color" : "hud.steveparty.stencil_gun.hint_stencil",
                MODE_KEY.getBoundKeyLocalizedText());
        context.drawCenteredTextWithShadow(client.textRenderer, hint, width / 2, y - 10, HINT_COLOR);
    }
}
