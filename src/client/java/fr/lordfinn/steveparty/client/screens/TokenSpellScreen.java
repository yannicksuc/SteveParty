package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.tokenspell.MobTextureColors;
import fr.lordfinn.steveparty.client.tokenspell.TokenSpellPreview;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import fr.lordfinn.steveparty.payloads.custom.TokenSpellPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.MAX_TOKEN_SIZE;
import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.MIN_TOKEN_SIZE;
import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.NO_COLOR;
import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.TOKEN_SIZE_STEP;

/**
 * Token spell of the Tokenizer Wand: a small, non-pausing "magic circle" overlay at the bottom of the screen with a
 * size slider. The targeted mob is rendered live at the chosen size ({@link TokenSpellPreview}) and a ring of
 * particles of the token colour marks its future height. Enter / "Cast" sends the spell, Esc / "Cancel" closes
 * without doing anything. The world keeps being rendered (no blur, no dark background).
 */
public class TokenSpellScreen extends Screen {
    private static final int RUNE_COLOR = 0xC150EB;
    private static final int GLOW_COLOR = 0xE9B8FF;
    private static final Identifier RUNE_FONT = Identifier.ofVanilla("alt"); // enchanting table glyphs
    private static final String RUNES = "abcdefghijklmnopqrstuvwxyz";
    private static final int RUNE_COUNT = 18;
    private static final int SPARKLE_COUNT = 22;
    private static final int RING_RX = 124;
    private static final int RING_RY = 48;
    /** Beyond this distance (squared, blocks) the spell screen closes by itself. */
    private static final double MAX_DISTANCE_SQUARED = 10 * 10;

    private final MobEntity mob;
    private final boolean colorKept;
    private final int color;
    private final Text tokenName;
    private float size;
    private int ticks;
    private SizeSlider slider;

    public TokenSpellScreen(MobEntity mob, float initialSize, boolean resize, int currentColor) {
        super(Text.translatableWithFallback(resize ? "screen.steveparty.token_spell.resize_title" : "screen.steveparty.token_spell.title",
                resize ? "Resizing spell" : "Token spell"));
        this.mob = mob;
        this.size = snap(initialSize);
        this.colorKept = resize && currentColor != NO_COLOR;
        // Computed once: when the texture has tied colours, the pick is random and must not change while sliding
        this.color = colorKept ? currentColor : MobTextureColors.pickColor(mob);
        // Same name as the server will give: the mob's custom name if any, else the player's name
        PlayerEntity player = MinecraftClient.getInstance().player;
        String name = mob.getCustomName() != null ? mob.getCustomName().getString()
                : player != null ? player.getDisplayName().getString() : "";
        MutableText styledName = Text.literal(name);
        this.tokenName = color == NO_COLOR ? styledName : styledName.withColor(color);
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = centerY();
        slider = addDrawableChild(new SizeSlider(cx - 80, cy - 8, 160, 20));
        addDrawableChild(ButtonWidget.builder(Text.translatableWithFallback("screen.steveparty.token_spell.cast", "Cast"),
                button -> confirm()).dimensions(cx - 81, cy + 16, 80, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatableWithFallback("screen.steveparty.token_spell.cancel", "Cancel"),
                button -> close()).dimensions(cx + 1, cy + 16, 80, 20).build());
        setInitialFocus(slider);
        TokenSpellPreview.set(mob.getId(), size);
    }

    private int centerY() {
        return height - RING_RY - 14;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        if (client == null || client.player == null || client.world == null || mob.isRemoved() || !mob.isAlive()
                || mob.getWorld() != client.world
                || client.player.squaredDistanceTo(mob) > MAX_DISTANCE_SQUARED
                || TokenizerWandItem.heldWand(client.player).isEmpty()) {
            close();
            return;
        }
        spawnHeightRing();
    }

    /** A ring of particles of the token colour at the future height of the token. */
    private void spawnHeightRing() {
        if (ticks % 2 != 0) return;
        EntityDimensions body = mob.getDimensions(EntityPose.STANDING);
        float current = Math.max(body.width(), body.height());
        if (current <= 0) return;
        float ratio = size / current;
        // A token stands on a base: its hitbox is higher than its body
        double baseOffset = Math.max(0, mob.getHeight() - body.height());
        double height = baseOffset + body.height() * ratio;
        double radius = body.width() * ratio / 2 + 0.25;
        DustParticleEffect dust = new DustParticleEffect(color == NO_COLOR ? RUNE_COLOR : color, 0.8F);
        for (int i = 0; i < 4; i++) {
            double angle = (ticks * 0.15) + i * Math.PI / 2;
            client.world.addParticle(dust, mob.getX() + Math.cos(angle) * radius, mob.getY() + height + 0.05,
                    mob.getZ() + Math.sin(angle) * radius, 0, 0, 0);
        }
    }

    private void confirm() {
        if (ClientPlayNetworking.canSend(TokenSpellPayload.ID)) {
            ClientPlayNetworking.send(new TokenSpellPayload(mob.getId(), size, color));
        }
        close();
    }

    @Override
    public void removed() {
        super.removed();
        TokenSpellPreview.clear();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                confirm();
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> {
                slider.setSize(size - TOKEN_SIZE_STEP);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> {
                slider.setSize(size + TOKEN_SIZE_STEP);
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            slider.setSize(size + Math.signum((float) verticalAmount) * TOKEN_SIZE_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ------------------------------------------------------------------ rendering

    /** No blur nor darkening: the previewed mob must stay visible. The magic circle is drawn instead. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        float time = ticks + delta;
        int cx = width / 2, cy = centerY();
        fillEllipse(context, cx, cy, RING_RX + 8, RING_RY + 6, 0x2A000000 | RUNE_COLOR);
        fillEllipse(context, cx, cy, RING_RX, RING_RY, 0xB0140822);
        dottedEllipse(context, cx, cy, RING_RX, RING_RY, 120, time * 0.004F, time);
        dottedEllipse(context, cx, cy, RING_RX - 14, RING_RY - 9, 90, -time * 0.006F, time + 20);
        drawRunes(context, cx, cy, time);
        drawSparkles(context, cx, cy, time);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = width / 2, cy = centerY();
        float pulse = 0.5F + 0.5F * MathHelper.sin((ticks + delta) * 0.15F);
        int titleColor = lerpColor(0xF3D27A, 0xFFFFFF, pulse);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("✦ ").append(title).append(" ✦"), cx, cy - RING_RY - 12, titleColor);
        context.drawCenteredTextWithShadow(textRenderer, mob.getType().getName(), cx, cy - 35, 0xFFE6E6FA);

        // Token name in its colour, with a colour swatch
        Text nameLine = colorKept
                ? Text.translatableWithFallback("screen.steveparty.token_spell.token_name_kept", "Token: %s (colour kept)", tokenName)
                : Text.translatableWithFallback("screen.steveparty.token_spell.token_name", "Token: %s", tokenName);
        int lineWidth = textRenderer.getWidth(nameLine) + 12;
        int x = cx - lineWidth / 2;
        int swatch = color == NO_COLOR ? 0xFF808080 : 0xFF000000 | color;
        context.fill(x - 1, cy - 23, x + 9, cy - 13, 0xFF000000 | GLOW_COLOR);
        context.fill(x, cy - 22, x + 8, cy - 14, swatch);
        context.drawTextWithShadow(textRenderer, nameLine, x + 12, cy - 22, 0xFFFFFFFF);

        context.drawCenteredTextWithShadow(textRenderer, Text.translatableWithFallback("screen.steveparty.token_spell.hint",
                "Enter: cast · Esc: cancel · Scroll/arrows: adjust"), cx, cy + RING_RY + 2, 0xFFB9A6C9);
    }

    private static void fillEllipse(DrawContext context, int cx, int cy, int rx, int ry, int argb) {
        for (int dy = -ry; dy <= ry; dy++) {
            double t = (double) dy / ry;
            int halfWidth = (int) Math.round(rx * Math.sqrt(Math.max(0, 1 - t * t)));
            context.fill(cx - halfWidth, cy + dy, cx + halfWidth, cy + dy + 1, argb);
        }
    }

    private static void dottedEllipse(DrawContext context, int cx, int cy, int rx, int ry, int dots, float rotation, float time) {
        for (int i = 0; i < dots; i++) {
            double angle = i * MathHelper.TAU / dots + rotation;
            int x = cx + (int) Math.round(Math.cos(angle) * rx);
            int y = cy + (int) Math.round(Math.sin(angle) * ry);
            // A bright wave travels along the ring
            float glow = 0.5F + 0.5F * MathHelper.sin(i * 0.35F - time * 0.25F);
            int alpha = (int) (90 + 165 * glow);
            context.fill(x, y, x + 1, y + 1, (alpha << 24) | lerpColor(RUNE_COLOR, GLOW_COLOR, glow));
        }
    }

    private void drawRunes(DrawContext context, int cx, int cy, float time) {
        int rx = RING_RX - 7, ry = RING_RY - 5;
        for (int i = 0; i < RUNE_COUNT; i++) {
            double angle = i * MathHelper.TAU / RUNE_COUNT - time * 0.005;
            int x = cx + (int) Math.round(Math.cos(angle) * rx);
            int y = cy + (int) Math.round(Math.sin(angle) * ry);
            // Skip the runes that would sit on the widgets / texts (middle band of the circle)
            if (Math.abs(x - cx) < 92 && Math.abs(y - cy) < RING_RY - 12) continue;
            float glow = 0.5F + 0.5F * MathHelper.sin(time * 0.12F + i * 1.7F);
            int alpha = (int) (110 + 145 * glow);
            char rune = RUNES.charAt((i * 7 + (int) (time / 40)) % RUNES.length());
            Text glyph = Text.literal(String.valueOf(rune)).setStyle(Style.EMPTY.withFont(RUNE_FONT));
            context.drawText(textRenderer, glyph, x - 3, y - 4, (alpha << 24) | lerpColor(RUNE_COLOR, GLOW_COLOR, glow), false);
        }
    }

    private void drawSparkles(DrawContext context, int cx, int cy, float time) {
        for (int i = 0; i < SPARKLE_COUNT; i++) {
            float period = 30 + (i * 13) % 25;
            float phase = (time + i * 11) / period;
            int cycle = (int) phase;
            float life = phase - cycle;
            float brightness = MathHelper.sin(life * MathHelper.PI);
            if (brightness < 0.25F) continue;
            // New random place on every cycle, on the ring or just around it
            long hash = (i * 73856093L) ^ (cycle * 19349663L);
            double angle = (hash & 0xFFFF) / 65535.0 * MathHelper.TAU;
            double spread = 0.8 + ((hash >> 16) & 0xFF) / 255.0 * 0.35;
            int x = cx + (int) Math.round(Math.cos(angle) * RING_RX * spread);
            int y = cy + (int) Math.round(Math.sin(angle) * RING_RY * spread);
            int alpha = (int) (255 * brightness);
            int argb = (alpha << 24) | 0xFFFFFF;
            context.fill(x, y, x + 1, y + 1, argb);
            if (brightness > 0.6F) {
                int arm = (alpha / 2 << 24) | GLOW_COLOR;
                context.fill(x - 1, y, x, y + 1, arm);
                context.fill(x + 1, y, x + 2, y + 1, arm);
                context.fill(x, y - 1, x + 1, y, arm);
                context.fill(x, y + 1, x + 1, y + 2, arm);
            }
        }
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) MathHelper.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) MathHelper.lerp(t, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ size

    private static float snap(float size) {
        float snapped = Math.round(size / TOKEN_SIZE_STEP) * TOKEN_SIZE_STEP;
        return MathHelper.clamp(snapped, MIN_TOKEN_SIZE, MAX_TOKEN_SIZE);
    }

    private Text sizeText() {
        String blocks = String.format(Locale.ROOT, "%.2f", size);
        EntityDimensions body = mob.getDimensions(EntityPose.STANDING);
        boolean wider = body.width() > body.height();
        return wider
                ? Text.translatableWithFallback("screen.steveparty.token_spell.width", "Width: %s blocks", blocks)
                : Text.translatableWithFallback("screen.steveparty.token_spell.height", "Height: %s blocks", blocks);
    }

    private class SizeSlider extends SliderWidget {
        SizeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Text.empty(), (size - MIN_TOKEN_SIZE) / (MAX_TOKEN_SIZE - MIN_TOKEN_SIZE));
            updateMessage();
        }

        void setSize(float newSize) {
            this.value = (snap(newSize) - MIN_TOKEN_SIZE) / (MAX_TOKEN_SIZE - MIN_TOKEN_SIZE);
            applyValue();
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(sizeText());
        }

        @Override
        protected void applyValue() {
            size = snap((float) (MIN_TOKEN_SIZE + value * (MAX_TOKEN_SIZE - MIN_TOKEN_SIZE)));
            TokenSpellPreview.set(mob.getId(), size);
        }
    }
}
