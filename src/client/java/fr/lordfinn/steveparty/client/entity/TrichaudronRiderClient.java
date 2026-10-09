package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronRiding;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;

/**
 * A player riding a Trichaudron: his attack and use keys fire the head he holds (or grab a wall in the air) instead of
 * attacking or using; his arms are held forward on the reins (PlayerEntityModelTrichaudronRiderMixin). While he rides
 * a tamed one, his HUD shows its jump's charge in a horse's jump bar (over the experience bar) and its fuel, its
 * tank's lava, right of the hotbar ({@link #drawHud}).
 */
public final class TrichaudronRiderClient {
    /** The use key, held last tick (a press is its edge: some inputs hold it without counting a press). */
    private static boolean useHeld;

    private TrichaudronRiderClient() {
    }

    /** Whether this entity rides a Trichaudron. */
    public static boolean riding(Entity entity) {
        return entity.getVehicle() instanceof TrichaudronEntity;
    }

    public static void initialize() {
        HudRenderCallback.EVENT.register(TrichaudronRiderClient::drawHud);
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (client.currentScreen != null || !riding(player)) return false;
            if (clickCount > 0) click();
            return true;
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            boolean held = client.options.useKey.isPressed();
            boolean fresh = held && !useHeld;
            useHeld = held;
            if (client.player == null || client.currentScreen != null || !riding(client.player)) return;
            boolean used = fresh;
            while (client.options.useKey.wasPressed()) used = true;
            if (used) click();
        });
    }

    private static final Identifier JUMP_BACKGROUND = Identifier.ofVanilla("hud/jump_bar_background");
    private static final Identifier JUMP_PROGRESS = Identifier.ofVanilla("hud/jump_bar_progress");

    /** The jump's charge (a horse's jump bar, over the experience bar) and its fuel, right of the hotbar. */
    private static void drawHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || !(client.player.getVehicle() instanceof TrichaudronEntity trichaudron)
                || !trichaudron.isSteered()) {
            return;
        }
        int x = context.getScaledWindowWidth() / 2 - 91, y = context.getScaledWindowHeight() - 29;
        context.drawGuiTexture(JUMP_BACKGROUND, x, y, 182, 5);
        int filled = Math.round(182 * trichaudron.getCharge() / (float) TrichaudronRiding.CHARGE_MAX);
        if (filled > 0) context.drawGuiTexture(JUMP_PROGRESS, 182, 5, 0, 0, x, y, filled, 5);
        Text fuel = Text.translatable("screen.steveparty.trichaudron.tank", trichaudron.getTank(), TrichaudronEntity.TANK_MAX);
        int color = trichaudron.getTank() == 0 ? 0xFFFF5555 : 0xFFFF9A3C;
        context.drawTextWithShadow(client.textRenderer, fuel, x + 182 + 8, context.getScaledWindowHeight() - 15, color);
    }

    private static void click() {
        if (ClientPlayNetworking.canSend(TrichaudronEvents.RiderClick.ID)) ClientPlayNetworking.send(new TrichaudronEvents.RiderClick());
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.swingHand(Hand.MAIN_HAND);
    }
}
