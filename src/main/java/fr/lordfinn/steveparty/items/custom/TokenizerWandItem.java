package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.effect.SquishEffect;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
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
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;
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

    /**
     * Bounds of the token size: its biggest dimension (height, or width when wider than tall), in blocks.
     * 0.25 keeps a token clickable and its name readable; 2 blocks keeps it smaller than a board space column and
     * below the height of a player. The default (1 block) is the size tokens always had.
     */
    public static final float MIN_TOKEN_SIZE = 0.25F;
    public static final float MAX_TOKEN_SIZE = 2.0F;
    public static final float DEFAULT_TOKEN_SIZE = 1.0F;
    /** Slider / rounding step of the token size, in blocks. */
    public static final float TOKEN_SIZE_STEP = 0.05F;
    public static final int NO_COLOR = -1;
    /** Duration of the squish animation (and of the levitation of a new token), in ticks. */
    public static final int SQUISH_DURATION = 130;
    /** Ticks during which the wand can't cast again (anti-spam of the C2S payload). */
    public static final int SPELL_COOLDOWN = 10;
    /** Extra reach beyond the player's entity interaction range: the spell screen does not pause the game. */
    private static final double SPELL_EXTRA_REACH = 1.0;

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
        if (!(entity instanceof MobEntity mob)) return super.useOnEntity(stack, user, entity, hand);
        TokenizedEntityInterface token = (TokenizedEntityInterface) mob;
        // Bosses can't become tokens (exploit: shrinking/controlling them)
        if (!token.steveparty$isTokenized() && isBoss(mob)) return ActionResult.FAIL;
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

    private static void openSpell(ServerPlayerEntity player, MobEntity mob) {
        TokenizedEntityInterface token = (TokenizedEntityInterface) mob;
        boolean resize = token.steveparty$isTokenized();
        float size = resize ? currentTokenSize(mob) : DEFAULT_TOKEN_SIZE;
        int color = resize ? token.steveparty$getTokenColor() : NO_COLOR;
        if (ServerPlayNetworking.canSend(player, OpenTokenSpellPayload.ID)) {
            ServerPlayNetworking.send(player, new OpenTokenSpellPayload(mob.getId(), size, resize, color));
        }
        player.getWorld().playSound(null, mob.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
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
        return clampTokenSize(Math.round(size / TOKEN_SIZE_STEP) * TOKEN_SIZE_STEP);
    }

    /** @return {@code size} clamped to [{@value #MIN_TOKEN_SIZE}, {@value #MAX_TOKEN_SIZE}] (default if not a number). */
    public static float clampTokenSize(float size) {
        if (!Float.isFinite(size)) return DEFAULT_TOKEN_SIZE;
        return MathHelper.clamp(size, MIN_TOKEN_SIZE, MAX_TOKEN_SIZE);
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
        ItemStack wand = heldWand(player);
        if (wand.isEmpty()) return SpellResult.NO_WAND;
        if (player.getItemCooldownManager().isCoolingDown(wand)) return SpellResult.COOLDOWN;
        Entity entity = player.getWorld().getEntityById(entityId);
        if (!(entity instanceof MobEntity mob) || !mob.isAlive()) return SpellResult.INVALID_TARGET;
        if (!player.canInteractWithEntity(mob, SPELL_EXTRA_REACH)) {
            MessageUtils.sendToPlayer(player, Text.translatableWithFallback("message.steveparty.token_spell_out_of_reach",
                    "The spell fizzles: the mob is out of reach."), MessageUtils.MessageType.ACTION_BAR);
            return SpellResult.OUT_OF_REACH;
        }
        TokenizedEntityInterface token = (TokenizedEntityInterface) mob;
        boolean resize = token.steveparty$isTokenized();
        if (!resize && isBoss(mob)) return SpellResult.BOSS;
        if (resize && !canControlToken(player, wand, mob)) {
            sendNotYourToken(player);
            return SpellResult.NOT_ALLOWED;
        }

        float size = clampTokenSize(requestedSize);
        int color = sanitizeColor(requestedColor);
        if (resize) {
            resizeToken(mob, size, color);
        } else {
            tokenizeEntity(mob, player, size, color);
        }
        player.getItemCooldownManager().set(wand, SPELL_COOLDOWN);
        return resize ? SpellResult.RESIZED : SpellResult.TOKENIZED;
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
                .flatMap(registry -> registry.getOptional(GAME_MASTER))
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

    public static boolean isBoss(MobEntity mob) {
        return mob instanceof EnderDragonEntity || mob instanceof WitherEntity;
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
        SquishEffect.squishToSize(mob, size, SQUISH_DURATION);
        mob.addStatusEffect(new StatusEffectInstance(LEVITATION, SQUISH_DURATION, 1));
        playSpellEffects(mob);
    }

    /** Resizes a token: owner, steps, status, name... are kept; the colour too, unless it was never set. */
    private static void resizeToken(MobEntity mob, float size, int color) {
        TokenizedEntityInterface token = (TokenizedEntityInterface) mob;
        if (token.steveparty$getTokenColor() == NO_COLOR && color != NO_COLOR) {
            applyColor(mob, null, color);
        }
        // No levitation: a token standing on a board space stays there
        SquishEffect.squishToSize(mob, size, SQUISH_DURATION);
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

    private static void playSpellEffects(MobEntity mob) {
        mob.getWorld().playSound(null, mob.getBlockPos(),
                SoundEvent.of(Identifier.ofVanilla("entity.illusioner.cast_spell")),
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        mob.getWorld().playSound(null, mob.getBlockPos(),
                SoundEvent.of(Identifier.ofVanilla("entity.zombie_villager.cure")),
                SoundCategory.PLAYERS, 0.2F, 2.0F);
        if (mob.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.WAX_OFF, mob.getX(), mob.getY() + mob.getHeight() / 2, mob.getZ(),
                    20, 0.3, 0.3, 0.3, 0.5);
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
        tooltip.add(Text.translatableWithFallback("tooltip.steveparty.tokenizer_wand.resize",
                "Use on one of your tokens: resize it").formatted(Formatting.GRAY));
        tooltip.add(Text.translatableWithFallback("tooltip.steveparty.tokenizer_wand.move",
                "To move a token, store it in a Token").formatted(Formatting.DARK_GRAY));
    }
}
