package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
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
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
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
    private static final float SOUND_VOLUME_1 = 0.2F;
    private static final float SOUND_PITCH_1 = 1.5F;
    private static final float SOUND_VOLUME_2 = 0.4F;
    private static final float SOUND_PITCH_2 = 1F;

    public DefaultDiceItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (isServerWorld(world)) {
            Vec3d spawnPosition = calculateSpawnPosition(player);
            DiceEntity diceEntity = spawnDiceEntity(world, spawnPosition);
            if (diceEntity != null) {
                configureDiceEntity(diceEntity, player, hand);
                playSounds(world, diceEntity);
                decrementDiceInHand(player, hand);
                diceEntity.startRoll();
            }
        }
        return TypedActionResult.success(player.getStackInHand(hand), world.isClient());
    }

    protected void decrementDiceInHand(PlayerEntity player, Hand hand) {
        player.getStackInHand(hand).decrementUnlessCreative(1, player);
    }

    protected boolean isServerWorld(World world) {
        return !world.isClient && world instanceof ServerWorld;
    }

    protected Vec3d calculateSpawnPosition(PlayerEntity player) {
        Vec3d playerPos = player.getPos();
        Vec3d lookVec = player.getRotationVec(1.0F).multiply(2);
        return playerPos.add(lookVec);
    }

    protected DiceEntity spawnDiceEntity(World world, Vec3d spawnPosition) {
        DiceEntity diceEntity = DICE_ENTITY.create(world);
        if (diceEntity != null) {
            diceEntity.setPosition(spawnPosition.x, spawnPosition.y + 0.5, spawnPosition.z);
            diceEntity.setNoGravity(true);
            world.spawnEntity(diceEntity);
        }
        return diceEntity;
    }

    /** Aims the die and gives it its roller and its item (the roll itself starts with {@link DiceEntity#startRoll}). */
    protected void configureDiceEntity(DiceEntity diceEntity, PlayerEntity player, Hand hand) {
        Vec3d velocity = player.getRotationVec(1.0F).multiply(VELOCITY_MULTIPLIER);
        diceEntity.setVelocity(velocity);
        diceEntity.setOwner(player.getUuid());
        diceEntity.findTarget(player.isSneaking() ? PlayerEntity.class : MobEntity.class);
        diceEntity.setItemReference(player.getStackInHand(hand).copyWithCount(1));
    }

    protected void playSounds(World world, DiceEntity diceEntity) {
        world.playSound(null, diceEntity.getBlockPos(), SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.AMBIENT, SOUND_VOLUME_1, SOUND_PITCH_1);
        world.playSound(null, diceEntity.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, SOUND_VOLUME_2, SOUND_PITCH_2);
    }

    /** The faces of a forged die, then one line per module. */
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        DiceFacesComponent faces = stack.get(DiceFacesComponent.TYPE);
        if (faces != null && !faces.faces().isEmpty()) tooltip.add(faces.describe());
        tooltip.addAll(DiceModules.tooltip(stack));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
