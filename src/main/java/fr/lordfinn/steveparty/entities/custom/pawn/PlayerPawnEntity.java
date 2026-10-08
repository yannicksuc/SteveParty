package fr.lordfinn.steveparty.entities.custom.pawn;

import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A player turned into a pawn: a little statue of the player (their skin, a big head) on a token base.
 * <p>
 * It is a regular token (tokenized mob, see {@code TokenEntityMixin}): moved on boards, stored in a Token, resized with
 * the wand like any pawn. On top of that, the player it was made from can be inside it ({@link #getPossessor}, see
 * {@link PawnPossessions}); the pawn itself never depends on it: when the player leaves, it stays, empty, playable.
 * <p>
 * The statue: right click cycles its {@linkplain PlayerPawnPose poses}; sneak + right click with an item makes it hold
 * the item (swapped with what it held), sneak + right click with an empty hand takes the item back. The held item is
 * its main hand: saved with it, kept in a Token, dropped when it dies (never lost, never copied).
 */
public class PlayerPawnEntity extends MobEntity {
    /** Whose skin the statue wears (the tokenized player). */
    private static final TrackedData<Optional<UUID>> SKIN_OWNER = DataTracker.registerData(PlayerPawnEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    /** Their name, for the skin lookup of a player who is not online. */
    private static final TrackedData<String> SKIN_NAME = DataTracker.registerData(PlayerPawnEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> POSE = DataTracker.registerData(PlayerPawnEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** The player inside the pawn, if any. Never saved: possession ends when the player leaves (see PawnPossessions). */
    private static final TrackedData<Optional<UUID>> POSSESSOR = DataTracker.registerData(PlayerPawnEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    /** Width and height of the body at scale 1 (a player's), so that a pawn's size matches a player's for the spell. */
    public static final float WIDTH = 0.6F;
    public static final float HEIGHT = 1.8F;
    /** The statue's eyes: in its big head. */
    public static final float EYE_HEIGHT = 1.5F;

    public PlayerPawnEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.setCanPickUpLoot(false);
        // Dropped by hand when it dies (see dropInventory): never by chance, never damaged
        this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN_OWNER, Optional.empty());
        builder.add(SKIN_NAME, "");
        builder.add(POSE, PlayerPawnPose.STAND.ordinal());
        builder.add(POSSESSOR, Optional.empty());
    }

    /** The statue wears the skin of the player {@code id} (named {@code name}). */
    public void setSkin(UUID id, String name) {
        this.dataTracker.set(SKIN_OWNER, Optional.of(id));
        this.dataTracker.set(SKIN_NAME, name);
    }

    public @Nullable UUID getSkinOwner() {
        return this.dataTracker.get(SKIN_OWNER).orElse(null);
    }

    public String getSkinName() {
        return this.dataTracker.get(SKIN_NAME);
    }

    public PlayerPawnPose getStatuePose() {
        return PlayerPawnPose.byIndex(this.dataTracker.get(POSE));
    }

    public void setStatuePose(PlayerPawnPose pose) {
        this.dataTracker.set(POSE, pose.ordinal());
    }

    public @Nullable UUID getPossessor() {
        return this.dataTracker.get(POSSESSOR).orElse(null);
    }

    /** Only {@link PawnPossessions} sets it. */
    void setPossessor(@Nullable UUID player) {
        this.dataTracker.set(POSSESSOR, Optional.ofNullable(player));
    }

    public boolean isPossessed() {
        return this.dataTracker.get(POSSESSOR).isPresent();
    }

    /** The statue holds this item (its main hand). */
    public ItemStack getHeldItem() {
        return this.getMainHandStack();
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        // One click, one action: the off hand only gets the click when the main hand did nothing
        if (hand != Hand.MAIN_HAND && !player.isSneaking()) return ActionResult.PASS;
        if (PawnPossessions.isInsideAPawn(player)) return ActionResult.FAIL;
        if (player.isSneaking()) return this.swapHeldItem(player, hand);
        if (this.getWorld().isClient) return ActionResult.SUCCESS;
        PlayerPawnPose pose = this.getStatuePose().next();
        this.setStatuePose(pose);
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_ARMOR_STAND_HIT,
                SoundCategory.NEUTRAL, 0.5F, 1.4F);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            MessageUtils.sendToPlayer(serverPlayer, Text.translatable("message.steveparty.player_pawn.pose", pose.displayName()),
                    MessageUtils.MessageType.ACTION_BAR);
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Sneak + right click: the item in {@code hand} goes into the statue's hand, and what it held comes back into
     * {@code hand} (nothing in hand: the item is just taken back). One move of each stack, server side: nothing copied.
     */
    private ActionResult swapHeldItem(PlayerEntity player, Hand hand) {
        ItemStack inHand = player.getStackInHand(hand);
        ItemStack held = this.getMainHandStack();
        if (inHand.isEmpty() && held.isEmpty()) return ActionResult.PASS;
        if (this.getWorld().isClient) return ActionResult.SUCCESS;
        player.setStackInHand(hand, held);
        this.equipStack(EquipmentSlot.MAINHAND, inHand);
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                inHand.isEmpty() ? SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM : SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM,
                SoundCategory.NEUTRAL, 1.0F, 1.0F);
        return ActionResult.SUCCESS;
    }

    /** The held item, whatever the game rules (doMobLoot) and enchantments: the item is the player's, not loot. */
    @Override
    protected void dropInventory() {
        super.dropInventory();
        ItemStack held = this.getMainHandStack();
        if (held.isEmpty()) return;
        this.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.dropStack(held);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        if (!this.getWorld().isClient) PawnPossessions.onPawnRemoved(this);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        UUID skinOwner = this.getSkinOwner();
        if (skinOwner != null) nbt.putUuid("SkinOwner", skinOwner);
        nbt.putString("SkinName", this.getSkinName());
        nbt.putString("StatuePose", this.getStatuePose().id());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(SKIN_OWNER, nbt.containsUuid("SkinOwner") ? Optional.of(nbt.getUuid("SkinOwner")) : Optional.empty());
        this.dataTracker.set(SKIN_NAME, nbt.getString("SkinName"));
        if (nbt.contains("StatuePose", NbtElement.STRING_TYPE)) this.setStatuePose(PlayerPawnPose.byId(nbt.getString("StatuePose")));
        // Hand drop chance as constructed (a pawn saved by a command with other chances must not drop twice)
        this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }
}
