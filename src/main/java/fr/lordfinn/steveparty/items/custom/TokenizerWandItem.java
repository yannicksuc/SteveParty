package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.effect.SquishEffect;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.pawn.PawnPossessions;
import fr.lordfinn.steveparty.payloads.custom.OpenTokenSpellPayload;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import fr.lordfinn.steveparty.particles.MagicShapeEffect;
import fr.lordfinn.steveparty.particles.SpellPalette;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.util.UseAction;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.lordfinn.steveparty.components.ModComponents.MOB_ENTITY_COMPONENT;
import static net.minecraft.entity.effect.StatusEffects.LEVITATION;

/**
 * Turns mobs into tokens, like a spell: using it on a mob opens (client side) the token spell screen, where the
 * token size is chosen with a slider and previewed live; confirming sends a {@code TokenSpellPayload} handled by
 * {@link #castSpell}. Used on an existing token (own one, or any with operator rights / Game Master), it resizes it.
 * <p>
 * Tokens are no longer moved with the wand: that goes through the Token item.
 */
public class TokenizerWandItem extends Item {
    /** Data-driven enchantment (data/steveparty/enchantment/game_master.json): control any player's token. */
    public static final RegistryKey<Enchantment> GAME_MASTER = RegistryKey.of(RegistryKeys.ENCHANTMENT, Steveparty.id("game_master"));
    /** Enchanting table enchantability of the wand (it can only receive steveparty:game_master). */
    private static final int ENCHANTABILITY = 10;

    /**
     * Bounds of the token size: its biggest dimension (height, or width when wider than tall), in blocks.
     * 0.25 keeps a token clickable and its name readable. The biggest is {@value #MAX_SIZE_FACTOR} times the mob's own
     * size ({@link #maxTokenSize}): a chicken pawn stays smaller than a zombie one. The default (1 block) is the size
     * tokens always had.
     */
    public static final float MIN_TOKEN_SIZE = 0.25F;
    public static final float MAX_SIZE_FACTOR = 5.0F;
    public static final float DEFAULT_TOKEN_SIZE = 1.0F;
    /** Slider / rounding step of the token size, in blocks. */
    public static final float TOKEN_SIZE_STEP = 0.05F;
    public static final int NO_COLOR = -1;
    /** Duration of the levitation of a new token, in ticks. */
    public static final int SQUISH_DURATION = 130;
    /**
     * Duration of the spell's transformation (the squish effect): the token grows / shrinks to its size in a few
     * jelly pulses, played by the clients (the hitbox gets its final size at once, server side).
     */
    public static final int TRANSFORM_DURATION = 40;
    /** Ticks during which the wand can't cast again (anti-spam of the C2S payload). */
    public static final int SPELL_COOLDOWN = 10;
    /**
     * Farthest a spell can reach, in blocks. Generous: the spell screen does not pause the game, and when the mob
     * wanders off while the circle is being drawn the spell is cast anyway (the client casts it right away).
     */
    public static final double MAX_SPELL_DISTANCE = 32.0;
    /**
     * A spell screen left open longer than this (ticks) no longer blocks the wand: its close was lost (the client
     * says when it closes it).
     */
    private static final int SPELL_OPEN_TIMEOUT = 20 * 60;
    /**
     * A token resized again within this many ticks of its last resize is resized quietly for the other players (the
     * caster still hears everything): the same spell over and over was unbearable for everyone around.
     */
    private static final int QUIET_RESIZE_TICKS = 100;

    /** Server: when each player's spell screen was opened (server ticks), until it is cast or closed. */
    private static final Map<UUID, Integer> OPEN_SPELLS = new HashMap<>();
    /** Server: when each token was last resized (server ticks). */
    private static final Map<UUID, Integer> LAST_RESIZES = new HashMap<>();

    public enum SpellResult {
        TOKENIZED, RESIZED, NO_WAND, COOLDOWN, INVALID_TARGET, OUT_OF_REACH, BOSS, NOT_ALLOWED;

        public boolean success() {
            return this == TOKENIZED || this == RESIZED;
        }
    }

    public TokenizerWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (entity instanceof PlayerEntity target) return useOnPlayer(user, target);
        if (!(entity instanceof MobEntity mob)) return super.useOnEntity(stack, user, entity, hand);
        TokenizedEntityInterface token = (TokenizedEntityInterface) mob;
        // Bosses can't become tokens (exploit: shrinking/controlling them)
        if (!token.steveparty$isTokenized() && isBoss(mob)) {
            sendBossRefused(user, mob);
            return ActionResult.FAIL;
        }
        // The server decides (it knows the token owner) and tells the client to open the spell screen
        if (user.getWorld().isClient) return ActionResult.SUCCESS;
        if (token.steveparty$isTokenized() && !canControlToken(user, stack, mob)) {
            sendNotYourToken(user);
            return ActionResult.FAIL;
        }
        if (user instanceof ServerPlayerEntity player) {
            openSpell(player, mob);
        }
        return ActionResult.SUCCESS;
    }

    /** The spell on another player: they become a player pawn (see {@link PawnPossessions}). */
    private static ActionResult useOnPlayer(PlayerEntity user, PlayerEntity target) {
        if (user.getWorld().isClient) return ActionResult.SUCCESS;
        if (!(user instanceof ServerPlayerEntity player)) return ActionResult.FAIL;
        if (!PawnPossessions.canTokenize(target) || PawnPossessions.isInsideAPawn(player)) {
            sendPlayerRefused(player);
            return ActionResult.FAIL;
        }
        openSpell(player, target);
        return ActionResult.SUCCESS;
    }

    private static void sendPlayerRefused(ServerPlayerEntity player) {
        MessageUtils.sendToPlayer(player, Text.translatableWithFallback("message.steveparty.player_pawn.refused",
                "The spell can't take this player now."), MessageUtils.MessageType.ACTION_BAR);
    }

    /**
     * Using the wand in the air (not on a mob): while the button is held, a flare of magic flies straight from the
     * wand where the player looks (see {@link TokenizerFlare}); hitting a mob the spell could take opens the spell on it.
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.pass(stack);
        // The press that sent the flare is still held after it hit (the spell screen is opening or open): the client
        // keeps using the wand, and each new flare would hit the same mob and open the spell again, over and over
        if (user instanceof ServerPlayerEntity player && isSpellOpen(player)) return TypedActionResult.fail(stack);
        user.setCurrentHand(hand);
        if (user instanceof ServerPlayerEntity player) TokenizerFlare.start(player, stack);
        return TypedActionResult.consume(stack);
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (user instanceof ServerPlayerEntity player) TokenizerFlare.tick(player, stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof ServerPlayerEntity player) TokenizerFlare.fizzle(player);
    }

    /** Enchanting table: the wand can only receive steveparty:game_master (see its supported items tag). */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantability() {
        return ENCHANTABILITY;
    }

    /** A mob the spell can take: alive, not a boss, and not someone else's token (unless allowed). */
    public static boolean isSpellTarget(PlayerEntity user, ItemStack wand, MobEntity mob) {
        if (!mob.isAlive()) return false;
        TokenizedEntityInterface token = (TokenizedEntityInterface) mob;
        if (token.steveparty$isTokenized()) return canControlToken(user, wand, mob);
        return !isBoss(mob);
    }

    /** Whether {@code player} has the spell screen open (the server opened it, the client did not close it yet). */
    public static boolean isSpellOpen(ServerPlayerEntity player) {
        Integer openedAt = OPEN_SPELLS.get(player.getUuid());
        if (openedAt == null) return false;
        if (player.getServer() != null && player.getServer().getTicks() - openedAt <= SPELL_OPEN_TIMEOUT) return true;
        OPEN_SPELLS.remove(player.getUuid());
        return false;
    }

    /** The player's spell screen closed (cast, cancelled, or the player left). */
    public static void spellClosed(UUID player) {
        OPEN_SPELLS.remove(player);
    }

    /** Opens the spell screen of {@code player} on {@code target}: a mob, or another player. */
    static void openSpell(ServerPlayerEntity player, LivingEntity target) {
        if (player.getServer() != null) OPEN_SPELLS.put(player.getUuid(), player.getServer().getTicks());
        boolean resize = target instanceof MobEntity mob && ((TokenizedEntityInterface) mob).steveparty$isTokenized();
        float size = resize ? currentTokenSize((MobEntity) target) : DEFAULT_TOKEN_SIZE;
        int color = resize ? ((TokenizedEntityInterface) target).steveparty$getTokenColor() : NO_COLOR;
        if (ServerPlayNetworking.canSend(player, OpenTokenSpellPayload.ID)) {
            ServerPlayNetworking.send(player, new OpenTokenSpellPayload(target.getId(), size, resize, color));
        }
        player.getWorld().playSound(null, target.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS, 1.0F, 1.2F);
    }

    /** Size of a token: the chosen one, or its current biggest dimension for tokens made before sizes were chosen. */
    public static float currentTokenSize(MobEntity mob) {
        float size = ((TokenizedEntityInterface) mob).steveparty$getTokenSize();
        if (size <= 0) {
            // Body only: the hitbox of a token also includes its base
            EntityDimensions body = mob.getDimensions(EntityPose.STANDING);
            size = Math.max(body.width(), body.height());
        }
        return clampTokenSize(mob, Math.round(size / TOKEN_SIZE_STEP) * TOKEN_SIZE_STEP);
    }

    /**
     * The entity's own size: its biggest dimension at its natural scale (whatever it was resized to), body only (not
     * the base of a token). A player: 1.8 blocks.
     */
    public static float naturalSize(LivingEntity entity) {
        EntityDimensions body = entity.getDimensions(EntityPose.STANDING);
        float scale = entity.getScale();
        float size = Math.max(body.width(), body.height()) / (scale > 0 ? scale : 1);
        return Float.isFinite(size) && size > 0 ? size : DEFAULT_TOKEN_SIZE;
    }

    /** The biggest token {@code entity} can become: {@value #MAX_SIZE_FACTOR} times its own size, on the size steps. */
    public static float maxTokenSize(LivingEntity entity) {
        float max = (float) Math.floor(naturalSize(entity) * MAX_SIZE_FACTOR / TOKEN_SIZE_STEP + 1.0E-3) * TOKEN_SIZE_STEP;
        return Math.max(MIN_TOKEN_SIZE, max);
    }

    /**
     * @return {@code size} clamped to [{@value #MIN_TOKEN_SIZE}, {@link #maxTokenSize} of {@code entity}] (the default
     * if not a number)
     */
    public static float clampTokenSize(LivingEntity entity, float size) {
        float max = maxTokenSize(entity);
        if (!Float.isFinite(size)) return Math.min(DEFAULT_TOKEN_SIZE, max);
        return MathHelper.clamp(size, MIN_TOKEN_SIZE, max);
    }

    /** @return {@code color} if it is a valid 0xRRGGBB colour, else {@link #NO_COLOR}. */
    public static int sanitizeColor(int color) {
        return color >= 0 && color <= 0xFFFFFF ? color : NO_COLOR;
    }

    /**
     * Server side of the token spell (C2S payload): everything the client sent is validated again, since the screen
     * does not pause the game and the payload can be forged.
     */
    public static SpellResult castSpell(ServerPlayerEntity player, int entityId, float requestedSize, int requestedColor) {
        spellClosed(player.getUuid());
        ItemStack wand = heldWand(player);
        if (wand.isEmpty()) return SpellResult.NO_WAND;
        if (player.getItemCooldownManager().isCoolingDown(wand.getItem())) return SpellResult.COOLDOWN;
        Entity entity = player.getWorld().getEntityById(entityId);
        if (entity instanceof ServerPlayerEntity target && target != player) return castOnPlayer(player, wand, target, requestedSize, requestedColor);
        if (!(entity instanceof MobEntity mob) || !mob.isAlive()) return SpellResult.INVALID_TARGET;
        if (mob.getWorld() != player.getWorld() || player.squaredDistanceTo(mob) > MAX_SPELL_DISTANCE * MAX_SPELL_DISTANCE) {
            MessageUtils.sendToPlayer(player, Text.translatableWithFallback("message.steveparty.token_spell_out_of_reach",
                    "The spell fizzles: the mob is out of reach."), MessageUtils.MessageType.ACTION_BAR);
            return SpellResult.OUT_OF_REACH;
        }
        TokenizedEntityInterface token = (TokenizedEntityInterface) mob;
        boolean resize = token.steveparty$isTokenized();
        if (!resize && isBoss(mob)) {
            sendBossRefused(player, mob);
            return SpellResult.BOSS;
        }
        if (resize && !canControlToken(player, wand, mob)) {
            sendNotYourToken(player);
            return SpellResult.NOT_ALLOWED;
        }

        float size = clampTokenSize(mob, requestedSize);
        int color = sanitizeColor(requestedColor);
        boolean othersHear = !resize || !resizedLately(mob);
        if (resize) {
            resizeToken(mob, player, size, color, othersHear);
        } else {
            tokenizeEntity(mob, player, size, color);
        }
        player.getItemCooldownManager().set(wand.getItem(), SPELL_COOLDOWN);
        playCastBurst(player, mob);
        if (othersHear) playCastSounds(player, mob);
        return resize ? SpellResult.RESIZED : SpellResult.TOKENIZED;
    }

    /** The spell cast on another player: they shrink, and become a player pawn (see {@link PawnPossessions}). */
    private static SpellResult castOnPlayer(ServerPlayerEntity player, ItemStack wand, ServerPlayerEntity target,
                                            float requestedSize, int requestedColor) {
        if (target.getWorld() != player.getWorld() || player.squaredDistanceTo(target) > MAX_SPELL_DISTANCE * MAX_SPELL_DISTANCE) {
            MessageUtils.sendToPlayer(player, Text.translatableWithFallback("message.steveparty.token_spell_out_of_reach",
                    "The spell fizzles: the mob is out of reach."), MessageUtils.MessageType.ACTION_BAR);
            return SpellResult.OUT_OF_REACH;
        }
        // One pawn per player: not one already under the spell or in a pawn (and not from inside a pawn)
        if (!PawnPossessions.canTokenize(target) || PawnPossessions.isInsideAPawn(player)) {
            sendPlayerRefused(player);
            return SpellResult.NOT_ALLOWED;
        }
        PawnPossessions.startSpell(target, player.getUuid(), clampTokenSize(target, requestedSize), sanitizeColor(requestedColor));
        player.getItemCooldownManager().set(wand.getItem(), SPELL_COOLDOWN);
        playSpellEffects(target);
        playCastBurst(player, target);
        playCastSounds(player, target);
        return SpellResult.TOKENIZED;
    }

    /** @return the Tokenizer Wand held by {@code player} (main hand first), or an empty stack. */
    public static ItemStack heldWand(PlayerEntity player) {
        for (Hand hand : Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof TokenizerWandItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Another player's token can only be resized by an operator, or with a Game Master wand.
     * Tokens without owner, and the user's own tokens, are always allowed.
     */
    public static boolean canControlToken(PlayerEntity user, ItemStack wand, MobEntity token) {
        UUID owner = ((TokenizedEntityInterface) token).steveparty$getTokenOwner();
        return owner == null
                || owner.equals(user.getUuid())
                || user.hasPermissionLevel(2)
                || hasGameMaster(wand, user.getWorld());
    }

    /** @return true if {@code stack} carries the data-driven {@code steveparty:game_master} enchantment. */
    public static boolean hasGameMaster(ItemStack stack, World world) {
        return world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT)
                .flatMap(registry -> registry.getEntry(GAME_MASTER))
                .map(enchantment -> EnchantmentHelper.getLevel(enchantment, stack) > 0)
                .orElse(false);
    }

    private static void sendNotYourToken(PlayerEntity user) {
        if (user instanceof ServerPlayerEntity serverPlayer) {
            MessageUtils.sendToPlayer(serverPlayer,
                    Text.translatableWithFallback("message.steveparty.token_not_yours",
                            "This token belongs to another player (operator or Game Master wand required)."),
                    MessageUtils.MessageType.ACTION_BAR);
        }
    }

    /** Lets the wand take the Wither too (off by default: shrinking or controlling it can be exploited). */
    public static final GameRules.Key<GameRules.BooleanRule> TOKENIZE_BOSSES = GameRuleRegistry.register(
            "stevepartyTokenizeBosses", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));

    /**
     * @return whether {@code mob} is a boss the wand must refuse: the Wither unless the game rule allows it, and always
     * the Ender Dragon (a flying body made of parts, steered by its fight phases: it can't stand still on a board).
     */
    public static boolean isBoss(MobEntity mob) {
        return mob instanceof EnderDragonEntity
                || (mob instanceof WitherEntity && !mob.getWorld().getGameRules().getBoolean(TOKENIZE_BOSSES));
    }

    /** Tells {@code user} (server side) why the boss {@code mob} can't become a pawn. */
    public static void sendBossRefused(PlayerEntity user, MobEntity mob) {
        if (!(user instanceof ServerPlayerEntity player)) return;
        Text message = mob instanceof EnderDragonEntity
                ? Text.translatableWithFallback("message.steveparty.token_dragon_refused",
                        "The Ender Dragon is far too big for the spell: it can't become a pawn.")
                : Text.translatableWithFallback("message.steveparty.token_boss_refused",
                        "The spell can't take a boss (game rule stevepartyTokenizeBosses).");
        MessageUtils.sendToPlayer(player, message, MessageUtils.MessageType.ACTION_BAR);
    }

    private static void tokenizeEntity(MobEntity mob, PlayerEntity user, float size, int color) {
        TokenizedEntityInterface token = (TokenizedEntityInterface) mob;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(user);
        if (color != NO_COLOR) {
            applyColor(mob, user, color);
        } else if (mob.getCustomName() == null) {
            mob.setCustomName(user.getDisplayName());
        }
        SquishEffect.squishToSize(mob, size, TRANSFORM_DURATION);
        mob.addStatusEffect(new StatusEffectInstance(LEVITATION, SQUISH_DURATION, 1));
        playSpellEffects(mob);
    }

    /**
     * Whether {@code mob} was resized within the last {@link #QUIET_RESIZE_TICKS} ticks; records this resize. Old
     * records are dropped as it goes.
     */
    private static boolean resizedLately(MobEntity mob) {
        if (mob.getServer() == null) return false;
        int now = mob.getServer().getTicks();
        LAST_RESIZES.values().removeIf(at -> now - at > QUIET_RESIZE_TICKS);
        return LAST_RESIZES.put(mob.getUuid(), now) != null;
    }

    /** Resizes a token: owner, steps, status, name... are kept; the colour too, unless it was never set. */
    private static void resizeToken(MobEntity mob, ServerPlayerEntity caster, float size, int color, boolean othersHear) {
        TokenizedEntityInterface token = (TokenizedEntityInterface) mob;
        if (token.steveparty$getTokenColor() == NO_COLOR && color != NO_COLOR) {
            applyColor(mob, null, color);
        }
        // No levitation: a token standing on a board space stays there
        SquishEffect.squishToSize(mob, size, TRANSFORM_DURATION, caster, othersHear);
        playSpellEffects(mob);
    }

    /**
     * Stores the colour on the token and styles its name with it (the start tile and the Token item read the colour
     * of the name). The name is the mob's custom name if it has one, else the name of {@code owner}.
     */
    private static void applyColor(MobEntity mob, PlayerEntity owner, int color) {
        ((TokenizedEntityInterface) mob).steveparty$setTokenColor(color);
        Text base = mob.getCustomName() != null ? mob.getCustomName() : owner != null ? owner.getDisplayName() : mob.getName();
        mob.setCustomName(Text.literal(base.getString()).withColor(color));
    }

    /**
     * The spell's zap (at the caster) and whoosh (at the mob), for the players around. Not for the caster: their
     * client already played them when the circle locked.
     */
    private static void playCastSounds(ServerPlayerEntity player, LivingEntity mob) {
        World world = player.getWorld();
        world.playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.TOKEN_SPELL_CAST,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        world.playSound(player, mob.getX(), mob.getY() + mob.getHeight() / 2, mob.getZ(), ModSounds.TOKEN_SPELL_CAST_WHOOSH,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    private static void playSpellEffects(LivingEntity mob) {
        // (the transformation's boings and sparkles are played by every client with the squish animation)
        if (mob.getWorld() instanceof ServerWorld world) {
            // A magic puff as the spell hits: coloured shapes bursting out of the mob, and sparkles
            double y = mob.getY() + mob.getHeight() / 2;
            world.spawnParticles(MagicShapeEffect.shape(1.4F, 0.82F, 0), mob.getX(), y, mob.getZ(),
                    24, 0.25, 0.3, 0.25, 0.25);
            world.spawnParticles(MagicShapeEffect.sparkle(1.3F, 0.9F, 0, SpellPalette.LILAC), mob.getX(), y, mob.getZ(),
                    14, 0.45, 0.5, 0.45, 0.02);
        }
    }

    /**
     * The spell: a stream of coloured shapes flies from the caster's wand to the mob. Each shape gets its own
     * speed and dies when it reaches the mob, so the stream stretches along the way. Particles only, no gameplay
     * effect. Sent to the other players around: the caster's own client already played it (validation phase of the
     * spell screen), from where the wand really is in first person.
     */
    private static void playCastBurst(ServerPlayerEntity player, LivingEntity mob) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;
        boolean mainHand = player.getMainHandStack().getItem() instanceof TokenizerWandItem;
        boolean rightHanded = (player.getMainArm() == Arm.RIGHT) == mainHand;
        float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double side = rightHanded ? 1 : -1;
        Vec3d look = player.getRotationVector();
        // About where the wand's tip is: in front of the player, on the side of the hand, below the eyes
        Vec3d from = player.getEyePos().add(look.multiply(0.5))
                .add(-MathHelper.cos(yaw) * 0.35 * side, -0.25, -MathHelper.sin(yaw) * 0.35 * side);
        Vec3d to = new Vec3d(mob.getX(), mob.getY() + mob.getHeight() / 2, mob.getZ());
        Vec3d path = to.subtract(from);
        var random = player.getRandom();
        for (int i = 0; i < 16; i++) {
            int life = 6 + random.nextInt(12);
            Vec3d velocity = path.multiply(1.0 / life).add((random.nextDouble() - 0.5) * 0.06,
                    (random.nextDouble() - 0.5) * 0.06, (random.nextDouble() - 0.5) * 0.06);
            for (ServerPlayerEntity viewer : world.getPlayers()) {
                if (viewer == player || viewer.squaredDistanceTo(from) > 48 * 48) continue;
                // count 0: the "delta" is the exact velocity of the particle
                world.spawnParticles(viewer, MagicShapeEffect.shape(1.0F, 1.0F, life), false, from.x, from.y, from.z, 0,
                        velocity.x, velocity.y, velocity.z, 1.0);
            }
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        // Token selected by older versions (the wand used to move tokens): no longer used
        if (!world.isClient && stack.contains(MOB_ENTITY_COMPONENT)) {
            stack.remove(MOB_ENTITY_COMPONENT);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatableWithFallback("tooltip.steveparty.tokenizer_wand.tokenize",
                "Use on a mob: cast the token spell and choose its size").formatted(Formatting.GRAY));
        tooltip.add(Text.translatableWithFallback("tooltip.steveparty.tokenizer_wand.player",
                "Use on a player: they become a pawn (sneak to get out)").formatted(Formatting.GRAY));
        tooltip.add(Text.translatableWithFallback("tooltip.steveparty.tokenizer_wand.resize",
                "Use on one of your tokens: resize it").formatted(Formatting.GRAY));
        tooltip.add(Text.translatableWithFallback("tooltip.steveparty.tokenizer_wand.move",
                "To move a token, store it in a Token").formatted(Formatting.DARK_GRAY));
    }
}
