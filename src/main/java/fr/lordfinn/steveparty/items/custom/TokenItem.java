package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.components.EntityDataComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.ENTITY_DATA_COMPONENT;
import static net.minecraft.sound.SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE;

public class TokenItem extends Item {

    public TokenItem(Settings settings) {
        super(settings.maxCount(1));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        EntityDataComponent dataComponent = stack.get(ENTITY_DATA_COMPONENT);
        if (dataComponent != null) {
            tooltip.add(Text.literal("Use on block to summon it.").formatted(Formatting.GRAY));
        }
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (entity instanceof HidingTraderEntity merchant) {
            return handleMerchantInteraction(stack, user, merchant, hand);
        }

        if (isValidTokenization(stack, user, entity)) {
            handleTokenization(stack, user, entity, hand);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private ActionResult handleMerchantInteraction(ItemStack stack, PlayerEntity user, HidingTraderEntity merchant, Hand hand) {
        World world = user.getWorld();

        if (!world.isClient) {
            EntityDataComponent dataComponent = stack.get(ENTITY_DATA_COMPONENT);
            if (dataComponent != null) {
                // Summon entity from token and make the merchant invisible
                NbtCompound entityData = dataComponent.entityData();
                Entity entity = createEntityFromData(world, entityData);

                if (entity != null) {
                    positionAndSpawnEntity(world, merchant.getBlockPos(), dataComponent.attributesData(), entity);
                    entity.startRiding(merchant, true);
                    merchant.setInvisible(true);

                    user.setStackInHand(hand, clearTokenData(stack));
                    playSound(world, merchant.getBlockPos(), 1.5F);

                    MessageUtils.sendToPlayer((ServerPlayerEntity) user, "Entity has been summoned and attached to the merchant!", MessageUtils.MessageType.ACTION_BAR);
                    return ActionResult.SUCCESS;
                }
            } else if (stack.get(ENTITY_DATA_COMPONENT) == null && merchant.hasPassengers()) {
                // Capture the merchant's passenger into the empty token
                Entity passenger = merchant.getFirstPassenger();
                if (passenger instanceof MobEntity mobEntity) {
                    passenger.stopRiding();
                    handleTokenization(stack, user, mobEntity, hand);
                    merchant.setInvisible(false);

                    MessageUtils.sendToPlayer((ServerPlayerEntity) user, "Entity has been captured from the merchant!", MessageUtils.MessageType.ACTION_BAR);
                    return ActionResult.SUCCESS;
                }
            }
        }
        return ActionResult.PASS;
    }

    private boolean isValidTokenization(ItemStack stack, PlayerEntity user, LivingEntity entity) {
        return stack.get(ENTITY_DATA_COMPONENT) == null
                && !user.getWorld().isClient
                && entity instanceof MobEntity
                && ((TokenizedEntityInterface) entity).steveparty$isTokenized();
    }

    private void handleTokenization(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        NbtCompound entityData = createEntityData(entity);
        NbtList attributesData = entity.getAttributes().toNbt();

        EntityDataComponent dataComponent = new EntityDataComponent(attributesData, entityData);
        stack.set(ModComponents.ENTITY_DATA_COMPONENT, dataComponent);

        Text customName = entity.getCustomName() != null ? entity.getCustomName() : entity.getName();
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Token of ").append(customName));

        user.setStackInHand(hand, stack);
        entity.remove(Entity.RemovalReason.DISCARDED);
        playSound(user.getWorld(), user.getBlockPos(), 1.0F);

        MessageUtils.sendToPlayer((ServerPlayerEntity) user, "The token has been captured!", MessageUtils.MessageType.ACTION_BAR);
    }

    private NbtCompound createEntityData(LivingEntity entity) {
        NbtCompound entityData = new NbtCompound();
        entityData.putString("id", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
        entity.writeNbt(entityData);
        return entityData;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!context.getWorld().isClient) {
            return handleBlockUsage(context);
        }
        return ActionResult.PASS;
    }

    private ActionResult handleBlockUsage(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        BlockPos blockPos = context.getBlockPos();
        World world = context.getWorld();
        ItemStack stack = context.getStack();
        Hand hand = context.getHand();

        EntityDataComponent dataComponent = stack.get(ModComponents.ENTITY_DATA_COMPONENT);
        if (dataComponent != null) {
            return summonEntityFromToken(world, blockPos, stack, dataComponent, player, hand);
        }
        return ActionResult.PASS;
    }

    private ActionResult summonEntityFromToken(World world, BlockPos blockPos, ItemStack stack, EntityDataComponent dataComponent, PlayerEntity player, Hand hand) {
        NbtCompound entityData = dataComponent.entityData();
        NbtList attributesData = dataComponent.attributesData();

        Entity entity = createEntityFromData(world, entityData);
        if (entity != null) {
            positionAndSpawnEntity(world, blockPos, attributesData, entity);
            player.setStackInHand(hand, clearTokenData(stack));
            playSound(world, blockPos, 1.5F);

            MessageUtils.sendToPlayer((ServerPlayerEntity) player, "The token has been summoned!", MessageUtils.MessageType.ACTION_BAR);
            return ActionResult.SUCCESS;
        }
        return ActionResult.FAIL;
    }

    private Entity createEntityFromData(World world, NbtCompound entityData) {
        return EntityType.loadEntityWithPassengers(entityData, world, SpawnReason.COMMAND, e -> e);
    }

    private void positionAndSpawnEntity(World world, BlockPos blockPos, NbtList attributesData, Entity entity) {
        VoxelShape shape = world.getBlockState(blockPos).getCollisionShape(world, blockPos);
        double blockHeight = shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y);

        entity.refreshPositionAndAngles(
                blockPos.getX() + 0.5,
                blockPos.getY() + blockHeight,
                blockPos.getZ() + 0.5,
                entity.getYaw(),
                entity.getPitch()
        );

        if (entity instanceof MobEntity mobEntity) {
            mobEntity.getAttributes().readNbt(attributesData);
        }

        world.spawnEntity(entity);
    }

    private ItemStack clearTokenData(ItemStack stack) {
        stack.remove(ModComponents.ENTITY_DATA_COMPONENT);
        stack.remove(DataComponentTypes.CUSTOM_NAME);
        return stack;
    }

    private void playSound(World world, BlockPos pos, float pitch) {
        world.playSound(null, pos, BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0F, pitch);
    }
}
