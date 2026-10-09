package fr.lordfinn.steveparty.entities.custom.mistigri;

import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.components.DiceFacesComponent.Kind;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.custom.DefaultDiceItem;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.CatVariant;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.List;

/**
 * How a Mistigri comes about: by a stroke of bad luck.
 * <ul>
 *     <li>a vanilla <b>black cat</b> (the all-black variant, the witches' cat), in a <b>witch hut</b> or within
 *     {@link #HUT_RANGE} blocks of one;</li>
 *     <li>a die of the mod <b>bound</b> to it: used on the cat (right-click it with the die: the die floats to it and
 *     rolls above it, {@link DefaultDiceItem#throwAt}), or thrown with the cat the nearest mob;</li>
 *     <li>the roll lands on a <b>1</b>, whatever its colour (normal, premium, cursed...), or on a <b>0</b>: the cat
 *     swells into a Mistigri (his summon animation, witch sparks, smoke, a heavy hiss). Any other result: the cat only
 *     glares and hisses.</li>
 * </ul>
 * A tamed black cat stays its owner's. Server side.
 */
public final class MistigriSummoning {
    /** Within this many blocks of a witch hut's walls. */
    public static final int HUT_RANGE = 16;

    /** For the GameTests (no witch hut in a test structure): boxes counting as witch huts, none in game. */
    @VisibleForTesting
    public static final java.util.Set<Box> TEST_HUTS = new java.util.HashSet<>();

    private MistigriSummoning() {
    }

    public static void initialize() {
        // a die used on a cat is thrown bound to it
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player.isSpectator() || !(entity instanceof CatEntity cat) || !cat.isAlive()) return ActionResult.PASS;
            if (!(player.getStackInHand(hand).getItem() instanceof DefaultDiceItem)) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            return DefaultDiceItem.throwAt(player, hand, cat).isAccepted() ? ActionResult.SUCCESS : ActionResult.FAIL;
        });
    }

    /** A cat a die may turn into a Mistigri: the all-black one. */
    public static boolean isBlackCat(Entity entity) {
        return entity instanceof CatEntity cat && cat.isAlive() && cat.getVariant().matchesKey(CatVariant.ALL_BLACK);
    }

    /** The faces bring bad luck: numbers only, adding up to 1 or 0 (a 1 of any colour, a 0). */
    public static boolean unlucky(List<DiceFace> faces) {
        if (faces.isEmpty()) return false;
        int sum = 0;
        for (DiceFace face : faces) {
            if (!face.kind().isNumeric() || face.kind() == Kind.BLANK) return false;
            sum += face.value();
        }
        return sum == 0 || sum == 1;
    }

    /** Whether {@code pos} is in a witch hut or within {@link #HUT_RANGE} blocks of one (its loaded chunks). */
    public static boolean nearWitchHut(ServerWorld world, BlockPos pos) {
        for (Box hut : TEST_HUTS) if (hut.expand(HUT_RANGE).contains(Vec3d.ofCenter(pos))) return true;
        Structure hut = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(StructureKeys.SWAMP_HUT);
        if (hut == null) return false;
        ChunkPos center = new ChunkPos(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded(center.x + dx, center.z + dz)) continue;
                for (StructureStart start : world.getStructureAccessor().getStructureStarts(new ChunkPos(center.x + dx, center.z + dz),
                        structure -> structure == hut)) {
                    if (start.hasChildren() && start.getBoundingBox().expand(HUT_RANGE).contains(pos)) return true;
                }
            }
        }
        return false;
    }

    /** A die's roll is final (DiceEntity): if it was bound to a black cat by a witch hut, it may summon a Mistigri. */
    public static void onRollFinished(ServerWorld world, DiceEntity die, List<DiceFace> faces) {
        Entity target = die.getTarget().map(world::getEntity).orElse(null);
        if (!isBlackCat(target) || !nearWitchHut(world, target.getBlockPos())) return;
        CatEntity cat = (CatEntity) target;
        if (unlucky(faces)) {
            transform(world, cat);
        } else {
            Vec3d at = cat.getPos().add(0, 0.5, 0);
            world.spawnParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.01);
            world.playSound(null, at.x, at.y, at.z, ModSounds.MISTIGRI_HISS, SoundCategory.NEUTRAL, 0.5f, 1.6f);
        }
    }

    /** The cat swells into a Mistigri, where it stood, facing the same way. Null if it could not be made. */
    public static @Nullable MistigriEntity transform(ServerWorld world, CatEntity cat) {
        MistigriEntity mistigri = ModEntities.MISTIGRI.create(world);
        if (mistigri == null) return null;
        mistigri.refreshPositionAndAngles(cat.getX(), cat.getY(), cat.getZ(), cat.getYaw(), 0);
        mistigri.setHeadYaw(cat.getHeadYaw());
        mistigri.setBodyYaw(cat.getBodyYaw());
        if (cat.hasCustomName()) mistigri.setCustomName(cat.getCustomName());
        if (cat.isTamed() && cat.getOwnerUuid() != null) {
            mistigri.setOwnerUuid(cat.getOwnerUuid());
            mistigri.setTamed(true, true);
        }
        mistigri.setPersistent();
        mistigri.act(MistigriEntity.Action.SUMMON);
        cat.discard();
        world.spawnEntity(mistigri);
        Vec3d at = mistigri.getPos().add(0, 0.7, 0);
        world.spawnParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 40, 0.6, 0.6, 0.6, 0.1);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 16, 0.5, 0.4, 0.5, 0.02);
        world.spawnParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 10, 0.4, 0.3, 0.4, 0.05);
        world.playSound(null, at.x, at.y, at.z, ModSounds.MISTIGRI_TRANSFORM, SoundCategory.NEUTRAL, 1.2f, 1.0f);
        MessageUtils.sendToNearby(world, at, 24, Text.translatable("message.steveparty.mistigri.summoned")
                .formatted(Formatting.DARK_PURPLE), MessageUtils.MessageType.ACTION_BAR);
        return mistigri;
    }
}
