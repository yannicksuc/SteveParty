package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.powerups.PowerUpService;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;

import static fr.lordfinn.steveparty.entities.ModEntities.DICE_ENTITY;

/**
 * A die: thrown with a right-click. It may carry faces of its own (forged in the Dice Forge:
 * {@link DiceFacesComponent}) and modules ({@link DiceModules}: from the forge or the crafting table); its tooltip
 * lists both.
 */
public class DefaultDiceItem extends Item {

    protected static final float VELOCITY_MULTIPLIER = 0.6F;
    /** The least upward speed of a throw (blocks per tick). */
    private static final double MIN_UPWARD_VELOCITY = 0.2;
    /** Thrown at the floor: share of the downward speed given back upward, and of the speed kept forward (a soft bounce). */
    private static final double FLOOR_BOUNCE = 0.35, FLOOR_FORWARD = 0.5;
    /** How far in front of the eyes the die appears, at most (blocks). */
    private static final double SPAWN_DISTANCE = 1.5;
    private static final float SOUND_VOLUME_1 = 0.2F;
    private static final float SOUND_PITCH_1 = 1.5F;
    private static final float SOUND_VOLUME_2 = 0.4F;
    private static final float SOUND_PITCH_2 = 1F;

    public DefaultDiceItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        // A power-up of the turn still at work (a player being picked, a warp), or a power-up die after another one
        if (isServerWorld(world) && PowerUpService.refusesRoll(player, player.getStackInHand(hand)))
            return TypedActionResult.fail(player.getStackInHand(hand));
        // A party listing its allowed dice refuses the others
        if (isServerWorld(world) && fr.lordfinn.steveparty.dice.AllowedDice.refusesThrow(player, player.getStackInHand(hand)))
            return TypedActionResult.fail(player.getStackInHand(hand));
        if (isServerWorld(world)) {
            Vec3d spawnPosition = calculateSpawnPosition(player);
            DiceEntity diceEntity = spawnDiceEntity(world, spawnPosition);
            if (diceEntity != null) {
                configureDiceEntity(diceEntity, player, hand);
                playSounds(world, diceEntity);
                PowerUpService.onDieThrown(player, player.getStackInHand(hand));
                decrementDiceInHand(player, hand);
                diceEntity.startRoll();
            }
        }
        return TypedActionResult.success(player.getStackInHand(hand), world.isClient());
    }

    /** The mob the die being thrown is bound to ({@link #throwAt}), null for an ordinary throw. Server thread. */
    private static @org.jetbrains.annotations.Nullable net.minecraft.entity.LivingEntity aimedAt;

    /**
     * Throws the die in {@code player}'s hand at {@code target}, bound to it: it floats to it and rolls above it rather
     * than at the nearest mob (a die used on a cat: see MistigriSummoning). The usual rules of a throw apply.
     */
    public static ActionResult throwAt(PlayerEntity player, Hand hand, net.minecraft.entity.LivingEntity target) {
        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof DefaultDiceItem item)) return ActionResult.PASS;
        aimedAt = target;
        try {
            return item.use(player.getWorld(), player, hand).getResult();
        } finally {
            aimedAt = null;
        }
    }

    protected void decrementDiceInHand(PlayerEntity player, Hand hand) {
        player.getStackInHand(hand).decrementUnlessCreative(1, player);
    }

    protected boolean isServerWorld(World world) {
        return !world.isClient && world instanceof ServerWorld;
    }

    /**
     * Where the die appears: in front of the thrower's eyes, short of any block in the way (looking at the floor or a
     * wall), raised until its box is free, so that it never starts inside the terrain.
     */
    protected Vec3d calculateSpawnPosition(PlayerEntity player) {
        World world = player.getWorld();
        Vec3d eyes = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F);
        BlockHitResult hit = world.raycast(new RaycastContext(eyes, eyes.add(look.multiply(SPAWN_DISTANCE)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        double reach = hit.getType() == HitResult.Type.MISS ? SPAWN_DISTANCE : Math.max(0, eyes.distanceTo(hit.getPos()) - 0.6);
        // The die's position is the bottom of its box: its centre goes on the line of sight
        Vec3d position = eyes.add(look.multiply(reach)).subtract(0, DICE_ENTITY.getHeight() / 2, 0);
        for (int step = 0; step < 12 && !world.isSpaceEmpty(DICE_ENTITY.getDimensions().getBoxAt(position)); step++) {
            position = position.add(0, 0.25, 0);
        }
        return position;
    }

    protected DiceEntity spawnDiceEntity(World world, Vec3d spawnPosition) {
        DiceEntity diceEntity = DICE_ENTITY.create(world);
        if (diceEntity != null) {
            diceEntity.setPosition(spawnPosition.x, spawnPosition.y, spawnPosition.z);
            diceEntity.setNoGravity(true);
            world.spawnEntity(diceEntity);
        }
        return diceEntity;
    }

    /** Aims the die and gives it its roller and its item (the roll itself starts with {@link DiceEntity#startRoll}). */
    protected void configureDiceEntity(DiceEntity diceEntity, PlayerEntity player, Hand hand) {
        diceEntity.setVelocity(throwVelocity(player));
        diceEntity.setOwner(player.getUuid());
        if (aimedAt != null && aimedAt.isAlive()) diceEntity.setTargetEntity(aimedAt);
        else diceEntity.findTarget(player.isSneaking() ? PlayerEntity.class : MobEntity.class);
        diceEntity.setItemReference(player.getStackInHand(hand).copyWithCount(1));
    }

    /**
     * The die is tossed upward: thrown at the floor it bounces up softly (a small part of its speed, forward and up)
     * instead of digging in, thrown straight ahead it still rises a little; it then slows down and falls back on its
     * own (see DiceEntity).
     */
    protected Vec3d throwVelocity(PlayerEntity player) {
        Vec3d velocity = player.getRotationVec(1.0F).multiply(VELOCITY_MULTIPLIER);
        if (velocity.y >= 0) return new Vec3d(velocity.x, Math.max(MIN_UPWARD_VELOCITY, velocity.y), velocity.z);
        return new Vec3d(velocity.x * FLOOR_FORWARD, Math.max(MIN_UPWARD_VELOCITY, -velocity.y * FLOOR_BOUNCE),
                velocity.z * FLOOR_FORWARD);
    }

    protected void playSounds(World world, DiceEntity diceEntity) {
        world.playSound(null, diceEntity.getBlockPos(), SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.AMBIENT, SOUND_VOLUME_1, SOUND_PITCH_1);
        world.playSound(null, diceEntity.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, SOUND_VOLUME_2, SOUND_PITCH_2);
    }

    /**
     * A die carrying the Power-up module first shows what a power-up shows (its tags, what it does, when); then the
     * faces of a forged die, then one line per module.
     */
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        if (DiceModules.isPowerUp(stack)) {
            tooltip.add(PowerUpItem.tags());
            tooltip.add(PowerUpItem.point(Text.translatable("tooltip.steveparty.powerup.die.effect")
                    .formatted(Formatting.GRAY), Formatting.LIGHT_PURPLE));
            tooltip.add(PowerUpItem.point(PowerUpItem.when(), Formatting.LIGHT_PURPLE));
            tooltip.add(PowerUpItem.point(PowerUpItem.onePerTurn(), Formatting.LIGHT_PURPLE));
        }
        DiceFacesComponent faces = stack.get(DiceFacesComponent.TYPE);
        if (faces != null && !faces.faces().isEmpty()) tooltip.add(faces.describe());
        tooltip.addAll(DiceModules.tooltip(stack));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
