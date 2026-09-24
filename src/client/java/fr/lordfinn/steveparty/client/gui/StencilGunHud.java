package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.components.StencilGunSelection;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.payloads.custom.StencilGunScrollPayload;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
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
    private static final int PIXEL = 2;
    private static final int BOX = 16 * PIXEL + 4;
    private static final int ACTIVE = 0xFFFFD83D;
    private static final int INACTIVE = 0xFF555555;

    /** True: the wheel picks the colour, false: the stencil. */
    private static boolean colorMode = false;

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

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.currentScreen != null || !isHoldingGun(client)) return;
        ItemStack gun = client.player.getMainHandStack();
        List<ItemStack> contents = StencilGunItem.contents(gun);
        StencilGunSelection selection = StencilGunItem.validSelection(contents, StencilGunItem.selection(gun));
        StencilGunItem.Load load = StencilGunItem.selectedLoad(gun);

        int width = context.getScaledWindowWidth();
        int y = context.getScaledWindowHeight() - 59 - BOX - 12;
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
            context.getMatrices().scale(2, 2, 1);
            context.drawItem(dye, 0, 0);
            context.getMatrices().pop();
            context.drawText(client.textRenderer, Text.literal("×" + dye.getCount()), colorX + BOX - 14, y + BOX - 9, 0xFFFFFFFF, true);
        } else {
            Text engrave = Text.translatable("hud.steveparty.stencil_gun.engrave");
            context.drawCenteredTextWithShadow(client.textRenderer, engrave, colorX + BOX / 2, y + BOX / 2 - 4, 0xFFBBBBBB);
        }

        // Names and hint
        Text stencilName = load.shape() == null ? Text.translatable("tooltip.steveparty.stencil_gun.no_stencil")
                : StencilPatterns.byShape(load.shape()) != null ? StencilPatterns.byShape(load.shape()).name()
                : Text.translatable("tooltip.steveparty.stencil.custom");
        Text hint = Text.translatable(colorMode ? "hud.steveparty.stencil_gun.hint_color" : "hud.steveparty.stencil_gun.hint_stencil",
                MODE_KEY.getBoundKeyLocalizedText());
        context.drawCenteredTextWithShadow(client.textRenderer, stencilName, stencilX + BOX / 2, y + BOX + 2, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(client.textRenderer, hint, width / 2, y - 11, 0xFFDDDDDD);
    }
}
