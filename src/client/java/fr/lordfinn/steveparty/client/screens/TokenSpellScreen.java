package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.tokenspell.MobTextureColors;
import fr.lordfinn.steveparty.client.tokenspell.TokenSpellHand;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import fr.lordfinn.steveparty.particles.KamekShapeEffect;
import fr.lordfinn.steveparty.payloads.custom.TokenSpellPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.MAX_TOKEN_SIZE;
import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.MIN_TOKEN_SIZE;
import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.NO_COLOR;
import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.TOKEN_SIZE_STEP;

/**
 * Token spell of the Tokenizer Wand, in the style of Kamek's magic: no panel, the player draws a magic circle
 * directly on the screen, around the targeted mob (screen centre). Non-pausing: the world keeps being rendered.
 * <ol>
 *   <li>Charging (short): Kamek's shapes gather into the wand and around the mob, the circle forms.</li>
 *   <li>Drawing: press anywhere and drag, the circle's edge follows the cursor (its centre stays on the mob). The
 *   bigger the circle, the bigger the token: the radius is proportional to the size (a full-size circle is
 *   {@link TokenizerWandItem#MAX_TOKEN_SIZE} blocks). Scroll / arrows adjust it. The same circle, in shapes, is
 *   mirrored on the ground around the mob, and sparkles mark the token's future height. The first person wand
 *   follows the cursor ({@link TokenSpellHand}).</li>
 *   <li>Validation (Enter / right click): the circle locks and flashes, a stream of shapes flies from the wand to
 *   the mob, then the spell is sent.</li>
 *   <li>Transformation: played for everyone around by the squish animation (jelly growth pulses and swirling
 *   shapes, see SquishAnimations).</li>
 * </ol>
 * Esc cancels without doing anything.
 */
public class TokenSpellScreen extends Screen {
    private static final Identifier[] SPRITES = {Steveparty.id("textures/particle/kamek_circle.png"),
            Steveparty.id("textures/particle/kamek_triangle.png"), Steveparty.id("textures/particle/kamek_square.png"),
            Steveparty.id("textures/particle/kamek_sparkle.png")};
    private static final int SPARKLE = 3;
    private static final int CHARGE_TICKS = 16;
    private static final int VALIDATE_TICKS = 12;
    private static final int HEIGHT_MARK_COLOR = 0xC150EB;
    /** Beyond this distance (squared, blocks) the spell screen closes by itself. */
    private static final double MAX_DISTANCE_SQUARED = 10 * 10;
    /** Radius of the ground circle around the mob, per block of token size. */
    private static final double WORLD_RADIUS_PER_BLOCK = 0.6;

    private enum Phase { CHARGING, DRAWING, VALIDATING }

    private final MobEntity mob;
    private final boolean colorKept;
    private final int color;
    private final Text tokenName;
    private float size;
    private int ticks;
    private Phase phase = Phase.CHARGING;
    private int phaseTicks;
    private boolean dragging;
    private float shownRadius = -1;
    /** Centre of the circle: where the mob is drawn on the screen (followed smoothly). */
    private float centerX, centerY;
    private boolean centered;
    private Arm wandArm = Arm.RIGHT;
    /** Kamek shapes and sparkles drawn on the screen (GUI coordinates). */
    private final List<GuiShape> shapes = new ArrayList<>();

    private static final class GuiShape {
        float x, y, vx, vy;
        int age;
        final int life, sprite, color;

        GuiShape(float x, float y, float vx, float vy, int life, int sprite, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.sprite = sprite;
            this.color = color;
        }
    }

    public TokenSpellScreen(MobEntity mob, float initialSize, boolean resize, int currentColor) {
        super(Text.translatableWithFallback(resize ? "screen.steveparty.token_spell.resize_title" : "screen.steveparty.token_spell.title",
                resize ? "Resizing spell" : "Token spell"));
        this.mob = mob;
        this.size = snap(initialSize);
        this.colorKept = resize && currentColor != NO_COLOR;
        // Computed once: when the texture has tied colours, the pick is random and must not change while drawing
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
        centered = false;
        centerX = width / 2F;
        centerY = height / 2F;
        PlayerEntity player = client != null ? client.player : null;
        if (player != null) {
            boolean mainHand = player.getMainHandStack().getItem() instanceof TokenizerWandItem
                    || !(player.getOffHandStack().getItem() instanceof TokenizerWandItem);
            wandArm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ phases

    @Override
    public void tick() {
        super.tick();
        ticks++;
        phaseTicks++;
        if (client == null || client.player == null || client.world == null || mob.isRemoved() || !mob.isAlive()
                || mob.getWorld() != client.world
                || client.player.squaredDistanceTo(mob) > MAX_DISTANCE_SQUARED
                || TokenizerWandItem.heldWand(client.player).isEmpty()) {
            close();
            return;
        }
        switch (phase) {
            case CHARGING -> {
                chargeParticles();
                if (phaseTicks >= CHARGE_TICKS) setPhase(Phase.DRAWING);
            }
            case DRAWING -> {
                groundCircle();
                heightMarks();
                if (dragging) wandTrail();
                if (ticks % 4 == 0) emitAlongCircle(1, SPARKLE);
            }
            case VALIDATING -> {
                groundCircle();
                if (phaseTicks <= 8) shapeStream();
                if (phaseTicks >= VALIDATE_TICKS) {
                    confirm();
                    return;
                }
            }
        }
        tickShapes();
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        dragging = false;
        if (next == Phase.VALIDATING) {
            // The circle locks: a burst of shapes out of it
            burstFromCircle(18);
        }
    }

    private void validate() {
        if (phase == Phase.DRAWING) setPhase(Phase.VALIDATING);
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
        TokenSpellHand.clear();
    }

    // ------------------------------------------------------------------ world particles

    private Vec3d mobCenter() {
        return new Vec3d(mob.getX(), mob.getY() + mob.getHeight() / 2, mob.getZ());
    }

    /** Charging: shapes gather into the wand's tip, and around the mob. */
    private void chargeParticles() {
        Random random = client.world.random;
        Vec3d tip = TokenSpellHand.tipInWorld(0.7, wandArm);
        for (int i = 0; i < 2; i++) {
            Vec3d offset = new Vec3d(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).multiply(0.35);
            int life = 7;
            client.world.addParticle(KamekShapeEffect.shape(0.12F, 1.0F, life), tip.x + offset.x, tip.y + offset.y, tip.z + offset.z,
                    -offset.x / life, -offset.y / life, -offset.z / life);
        }
        Vec3d center = mobCenter();
        double radius = Math.max(1.0, mob.getWidth() + 0.6);
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * MathHelper.TAU;
            double dy = (random.nextDouble() - 0.5) * mob.getHeight();
            Vec3d from = center.add(Math.cos(angle) * radius, dy, Math.sin(angle) * radius);
            int life = 10;
            Vec3d velocity = center.subtract(from).multiply(1.0 / life)
                    .add(-Math.sin(angle) * 0.05, 0, Math.cos(angle) * 0.05); // spirals in
            client.world.addParticle(i == 0 ? KamekShapeEffect.sparkle(0.9F, 1.0F, life, KamekShapeEffect.RANDOM_COLOR)
                    : KamekShapeEffect.shape(0.8F, 1.0F, life), from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
        }
    }

    /** The drawn circle, mirrored in Kamek shapes on the ground around the mob. */
    private void groundCircle() {
        double radius = size * WORLD_RADIUS_PER_BLOCK;
        int points = 12;
        for (int i = 0; i < points; i++) {
            double angle = ticks * 0.09 + i * MathHelper.TAU / points;
            client.world.addParticle(KamekShapeEffect.shape(0.8F, 0F, 3), mob.getX() + Math.cos(angle) * radius,
                    mob.getY() + 0.15, mob.getZ() + Math.sin(angle) * radius, 0, 0, 0);
        }
    }

    /** Sparkles of the token colour at the future height of the token. */
    private void heightMarks() {
        if (ticks % 2 != 0) return;
        EntityDimensions body = mob.getDimensions(EntityPose.STANDING);
        float current = Math.max(body.width(), body.height());
        if (current <= 0) return;
        float ratio = size / current;
        // A token stands on a base: its hitbox is higher than its body
        double baseOffset = Math.max(0, mob.getHeight() - body.height());
        double height = baseOffset + body.height() * ratio;
        double radius = body.width() * ratio / 2 + 0.25;
        // Lightened: dark token colours (a cow's brown) would read as black specks
        KamekShapeEffect sparkle = KamekShapeEffect.sparkle(1.0F, 0F, 5,
                lerpColor(color == NO_COLOR ? HEIGHT_MARK_COLOR : color, 0xFFFFFF, 0.45F));
        for (int i = 0; i < 4; i++) {
            double angle = -ticks * 0.15 + i * Math.PI / 2;
            client.world.addParticle(sparkle, mob.getX() + Math.cos(angle) * radius, mob.getY() + height + 0.05,
                    mob.getZ() + Math.sin(angle) * radius, 0, 0, 0);
        }
    }

    /** Drawing: shapes trail from the wand's tip. */
    private void wandTrail() {
        Random random = client.world.random;
        Vec3d tip = TokenSpellHand.tipInWorld(0.7, wandArm);
        client.world.addParticle(KamekShapeEffect.shape(0.12F, 0.9F, 0), tip.x, tip.y, tip.z,
                (random.nextDouble() - 0.5) * 0.01, (random.nextDouble() - 0.5) * 0.01, (random.nextDouble() - 0.5) * 0.01);
    }

    /** Validation: Kamek's stream of shapes, from the wand's tip to the mob. Each dies as it reaches the mob. */
    private void shapeStream() {
        Random random = client.world.random;
        Vec3d tip = TokenSpellHand.tipInWorld(0.7, wandArm);
        Vec3d path = mobCenter().subtract(tip);
        for (int i = 0; i < 3; i++) {
            int life = 6 + random.nextInt(5);
            Vec3d velocity = path.multiply(1.0 / life).add((random.nextDouble() - 0.5) * 0.04,
                    (random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04);
            client.world.addParticle(i == 0 ? KamekShapeEffect.sparkle(0.5F, 1.0F, life, 0xFFFFFF)
                    : KamekShapeEffect.shape(0.45F, 1.0F, life), tip.x, tip.y, tip.z, velocity.x, velocity.y, velocity.z);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                validate();
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> {
                adjust(-TOKEN_SIZE_STEP);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> {
                adjust(TOKEN_SIZE_STEP);
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
            adjust(Math.signum((float) verticalAmount) * TOKEN_SIZE_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (phase != Phase.DRAWING) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = true;
            drawTo(mouseX, mouseY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            validate();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            drawTo(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** The circle is centred on the mob, wherever it is drawn on the screen. */
    private void followMob(float delta) {
        Vec3d center = mob.getLerpedPos(delta).add(0, mob.getHeight() / 2, 0);
        float[] screen = TokenSpellHand.worldToScreen(center);
        float x = screen == null ? width / 2F : (screen[0] + 1) * width / 2F;
        float y = screen == null ? height / 2F : (screen[1] + 1) * height / 2F;
        x = MathHelper.clamp(x, 0, width);
        y = MathHelper.clamp(y, 0, height);
        if (!centered) {
            centerX = x;
            centerY = y;
            centered = true;
        } else {
            centerX = MathHelper.lerp(0.3F, centerX, x);
            centerY = MathHelper.lerp(0.3F, centerY, y);
        }
    }

    /** The circle's edge goes through the cursor: its radius gives the size. */
    private void drawTo(double mouseX, double mouseY) {
        float radius = (float) Math.hypot(mouseX - centerX, mouseY - centerY);
        float previous = size;
        size = snap(radius / maxRadius() * MAX_TOKEN_SIZE);
        if (size != previous && client != null && client.world != null) {
            addShape(pointOnCircle(mouseX, mouseY, radiusFor(size)), 1.2F, client.world.random.nextInt(SPARKLE));
        }
    }

    private void adjust(float delta) {
        if (phase != Phase.DRAWING) return;
        size = snap(size + delta);
        if (client != null && client.world != null) emitAlongCircle(5, -1);
    }

    // ------------------------------------------------------------------ size

    private static float snap(float size) {
        float snapped = Math.round(size / TOKEN_SIZE_STEP) * TOKEN_SIZE_STEP;
        return MathHelper.clamp(snapped, MIN_TOKEN_SIZE, MAX_TOKEN_SIZE);
    }

    /** Radius (GUI pixels) of a full-size circle: fits the screen, the token texts above it. */
    private float maxRadius() {
        return MathHelper.clamp(height * 0.32F, 50, 140);
    }

    private float radiusFor(float tokenSize) {
        return tokenSize / MAX_TOKEN_SIZE * maxRadius();
    }

    private Text sizeText() {
        String blocks = String.format(Locale.ROOT, "%.2f", size);
        EntityDimensions body = mob.getDimensions(EntityPose.STANDING);
        boolean wider = body.width() > body.height();
        return wider
                ? Text.translatableWithFallback("screen.steveparty.token_spell.width", "Width: %s blocks", blocks)
                : Text.translatableWithFallback("screen.steveparty.token_spell.height", "Height: %s blocks", blocks);
    }

    // ------------------------------------------------------------------ screen shapes

    private float[] pointOnCircle(double towardsX, double towardsY, float radius) {
        double dx = towardsX - centerX, dy = towardsY - centerY;
        double length = Math.max(1.0E-3, Math.hypot(dx, dy));
        return new float[]{(float) (centerX + dx / length * radius), (float) (centerY + dy / length * radius),
                (float) (dx / length), (float) (dy / length)};
    }

    /** A shape popping out of a point of the circle, drifting outwards. */
    private void addShape(float[] point, float speed, int sprite) {
        Random random = client.world.random;
        addShape(point[0], point[1], point[2] * speed + (random.nextFloat() - 0.5F), point[3] * speed + (random.nextFloat() - 0.5F),
                10 + random.nextInt(8), sprite);
    }

    private void addShape(float x, float y, float vx, float vy, int life, int sprite) {
        if (shapes.size() >= 96) shapes.removeFirst();
        Random random = client.world.random;
        int shapeColor = sprite == SPARKLE && random.nextBoolean() ? 0xFFFFFF
                : KamekShapeEffect.COLORS[random.nextInt(KamekShapeEffect.COLORS.length)];
        shapes.add(new GuiShape(x, y, vx, vy, life, sprite, shapeColor));
    }

    /** {@code count} shapes (sprite -1: random shape) popping out of random points of the circle. */
    private void emitAlongCircle(int count, int sprite) {
        Random random = client.world.random;
        float radius = radiusFor(size);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * MathHelper.TAU;
            addShape(pointOnCircle(centerX + Math.cos(angle), centerY + Math.sin(angle), radius), 0.8F,
                    sprite < 0 ? random.nextInt(SPARKLE) : sprite);
        }
    }

    private void burstFromCircle(int count) {
        if (client == null || client.world == null) return;
        Random random = client.world.random;
        float radius = radiusFor(size);
        for (int i = 0; i < count; i++) {
            double angle = i * MathHelper.TAU / count + random.nextDouble() * 0.2;
            addShape(pointOnCircle(centerX + Math.cos(angle), centerY + Math.sin(angle), radius), 3.0F,
                    i % 3 == 0 ? SPARKLE : random.nextInt(SPARKLE));
        }
    }

    /** Charging: shapes spiral in from outside and settle on the forming circle. */
    private void chargeScreenShapes() {
        Random random = client.world.random;
        float radius = radiusFor(size);
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * MathHelper.TAU;
            double from = radius + 50 + random.nextDouble() * 40;
            float x = (float) (centerX + Math.cos(angle) * from), y = (float) (centerY + Math.sin(angle) * from);
            int life = 10 + random.nextInt(4);
            float targetX = (float) (centerX + Math.cos(angle + 0.6) * radius);
            float targetY = (float) (centerY + Math.sin(angle + 0.6) * radius);
            addShape(x, y, (targetX - x) / life, (targetY - y) / life, life, i == 0 ? SPARKLE : random.nextInt(SPARKLE));
        }
    }

    private void tickShapes() {
        if (phase == Phase.CHARGING) chargeScreenShapes();
        shapes.removeIf(shape -> ++shape.age >= shape.life);
        for (GuiShape shape : shapes) {
            shape.x += shape.vx;
            shape.y += shape.vy;
            // Charging shapes keep their speed (they must reach the circle), the others slow down
            if (phase != Phase.CHARGING) {
                shape.vx *= 0.88F;
                shape.vy *= 0.88F;
            }
        }
    }

    // ------------------------------------------------------------------ rendering

    /** No blur nor darkening, no panel: only the magic is drawn over the world. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        followMob(delta);
        // Follows the cursor every frame while the button is held (vanilla only sends drag events to a focused window)
        if (dragging) drawTo(mouseX, mouseY);
        float press = phase == Phase.VALIDATING ? 1 : dragging ? 0.7F : 0;
        TokenSpellHand.aim(width == 0 ? 0 : mouseX * 2F / width - 1, height == 0 ? 0 : mouseY * 2F / height - 1, press);
        super.render(context, mouseX, mouseY, delta);

        float time = ticks + delta;
        float target = radiusFor(size);
        float radius;
        float flash = 0;
        switch (phase) {
            case CHARGING -> {
                float t = MathHelper.clamp((phaseTicks + delta) / CHARGE_TICKS, 0, 1);
                // Forms with a little overshoot
                float c1 = 1.7F, c3 = c1 + 1;
                radius = target * (1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2));
                shownRadius = radius;
            }
            case VALIDATING -> {
                float t = MathHelper.clamp((phaseTicks + delta) / VALIDATE_TICKS, 0, 1);
                // Locks: two bright flashes, then tightens around the mob as the spell leaves
                flash = Math.max(0, MathHelper.sin(t * MathHelper.PI * 4)) * (1 - t * 0.5F);
                radius = target * (1 - 0.25F * t * t);
            }
            default -> {
                shownRadius = shownRadius < 0 ? target : MathHelper.lerp(0.35F, shownRadius, target);
                radius = shownRadius;
            }
        }
        drawMagicCircle(context, centerX, centerY, Math.max(0, radius), time, flash, phase == Phase.VALIDATING ? 2 : 1);
        if (phase == Phase.DRAWING && dragging) {
            float[] handle = pointOnCircle(mouseX, mouseY, radius);
            drawSprite(context, SPARKLE, handle[0], handle[1], 0xFFFFFF, 255);
        }
        drawShapes(context, delta);
        drawTexts(context);
    }

    /**
     * A soft, glowing, slightly irregular brush stroke in Kamek's four colours (flowing around), a fine inner line of
     * the token's colour, and twinkling sparkles riding on it.
     */
    private void drawMagicCircle(DrawContext context, float cx, float cy, float radius, float time, float flash, int thickness) {
        if (radius < 1) return;
        int points = Math.max(48, (int) (radius * MathHelper.TAU * 0.9F));
        int[] colors = KamekShapeEffect.COLORS;
        for (int i = 0; i < points; i++) {
            float angle = i * MathHelper.TAU / points;
            // Brush irregularity, slowly flowing
            float wobble = MathHelper.sin(angle * 6 + time * 0.2F) * 0.9F + MathHelper.sin(angle * 11 - time * 0.13F) * 0.5F;
            float r = radius + wobble;
            int x = Math.round(cx + MathHelper.cos(angle) * r);
            int y = Math.round(cy + MathHelper.sin(angle) * r);
            float along = (angle / MathHelper.TAU * colors.length + time * 0.03F) % colors.length;
            int index = (int) along;
            float blend = along - index;
            blend = blend * blend * (3 - 2 * blend);
            int rgb = lerpColor(colors[index], colors[(index + 1) % colors.length], blend);
            if (flash > 0) rgb = lerpColor(rgb, 0xFFFFFF, flash);
            context.fill(x - 1 - thickness / 2, y - 1 - thickness / 2, x + 1 + thickness, y + 1 + thickness, 0x26000000 | rgb);
            context.fill(x, y, x + thickness, y + thickness, 0xF0000000 | rgb);
        }
        // The token's colour, as a fine dotted line inside
        if (color != NO_COLOR && radius > 6) {
            for (int i = 0; i < points; i += 3) {
                float angle = i * MathHelper.TAU / points - time * 0.02F;
                int x = Math.round(cx + MathHelper.cos(angle) * (radius - 4));
                int y = Math.round(cy + MathHelper.sin(angle) * (radius - 4));
                context.fill(x, y, x + 1, y + 1, 0xA0000000 | color);
            }
        }
        // Sparkles riding on the circle
        for (int i = 0; i < 5; i++) {
            float angle = time * 0.05F + i * MathHelper.TAU / 5;
            float twinkle = 0.5F + 0.5F * MathHelper.sin(time * 0.6F + i * 2.1F);
            drawSprite(context, SPARKLE, cx + MathHelper.cos(angle) * radius, cy + MathHelper.sin(angle) * radius,
                    i % 2 == 0 ? 0xFFFFFF : colors[i % colors.length], (int) (120 + 135 * twinkle));
        }
    }

    private void drawShapes(DrawContext context, float delta) {
        for (GuiShape shape : shapes) {
            float life = (shape.age + delta) / shape.life;
            int alpha = (int) (255 * (life < 0.6F ? 1 : Math.max(0, 1 - (life - 0.6F) / 0.4F)));
            if (alpha <= 8) continue;
            drawSprite(context, shape.sprite, shape.x + shape.vx * delta, shape.y + shape.vy * delta, shape.color, alpha);
        }
    }

    private void drawSprite(DrawContext context, int sprite, float x, float y, int rgb, int alpha) {
        context.drawTexture(RenderLayer::getGuiTextured, SPRITES[sprite], Math.round(x) - 4, Math.round(y) - 4, 0, 0,
                8, 8, 8, 8, (MathHelper.clamp(alpha, 0, 255) << 24) | rgb);
    }

    /** Minimal texts, above the circle: the token (name in its colour, swatch), its size, and how to cast. */
    private void drawTexts(DrawContext context) {
        int y = 8;
        Text nameLine = colorKept
                ? Text.translatableWithFallback("screen.steveparty.token_spell.token_name_kept", "Token: %s (colour kept)", tokenName)
                : Text.translatableWithFallback("screen.steveparty.token_spell.token_name", "Token: %s", tokenName);
        int lineWidth = textRenderer.getWidth(nameLine) + 12;
        int x = (width - lineWidth) / 2;
        int swatch = color == NO_COLOR ? 0xFF808080 : 0xFF000000 | color;
        context.fill(x - 1, y - 1, x + 9, y + 9, 0xFF241E1F);
        context.fill(x, y, x + 8, y + 8, swatch);
        context.drawTextWithShadow(textRenderer, nameLine, x + 12, y, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, sizeText(), width / 2, y + 11, 0xFFFFF4C8);
        Text hint = phase == Phase.CHARGING
                ? Text.translatableWithFallback("screen.steveparty.token_spell.charging", "The spell is charging...")
                : Text.translatableWithFallback("screen.steveparty.token_spell.hint",
                "Drag: draw the circle · Scroll: adjust · Enter/right click: cast · Esc: cancel");
        y += 23;
        for (OrderedText line : textRenderer.wrapLines(hint, width - 20)) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, 0xFFF0EAFF);
            y += 9;
        }
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) MathHelper.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) MathHelper.lerp(t, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}
