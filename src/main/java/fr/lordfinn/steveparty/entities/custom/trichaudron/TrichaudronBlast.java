package fr.lordfinn.steveparty.entities.custom.trichaudron;

import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The thermal blast, a head's line of scalding steam, and who it spares.
 * <ul>
 *     <li><b>Damage</b>: mostly fire ({@link #FIRE_DAMAGE}, none to whoever is immune to fire or under fire resistance)
 *     and a small physical part ({@link #PHYSICAL_DAMAGE}); burning {@link #FIRE_SECONDS} s (not the fire-proof).
 *     A player in heavy enchanted armour ({@link #ARMOUR_THRESHOLD} armour points and Protection levels adding up to
 *     {@link #PROTECTION_THRESHOLD}: full diamond Protection III, or netherite Protection IV) takes no physical part.
 *     The steam damage type ignores armour points (they only decide that threshold); Protection still eases it.</li>
 *     <li><b>The shove</b>: very hard, away from the head and a little up, whatever the armour.</li>
 *     <li><b>A shield</b> raised toward the jet stops it: its bearer takes nothing (no damage, no fire, no shove) and
 *     the steam goes no further than the shield (whoever stands behind is spared too).</li>
 *     <li>Spared always: the Trichaudron itself, other Trichaudrons, its riders, and the players this head trusts.</li>
 *     <li><b>The weak puff</b> of an empty tank: short, {@link #PUFF_DAMAGE} physical damage, no fire, a small shove.</li>
 * </ul>
 */
public final class TrichaudronBlast {
    public static final double RANGE = 30.0, RADIUS = 1.5;
    public static final float FIRE_DAMAGE = 4.0f, PHYSICAL_DAMAGE = 2.0f, FIRE_SECONDS = 4.0f;
    public static final double PUSH = 2.2, LIFT = 0.5;
    public static final double PUFF_RANGE = 6.0, PUFF_RADIUS = 1.2;
    public static final float PUFF_DAMAGE = 2.0f;
    public static final double PUFF_PUSH = 0.7, PUFF_LIFT = 0.25;
    public static final double ARMOUR_THRESHOLD = 20;
    public static final int PROTECTION_THRESHOLD = 12;
    /** A shield counts when its bearer looks at least this much toward the jet (cosine). */
    private static final double SHIELD_FACING = 0.2;

    private TrichaudronBlast() {
    }

    /** Whether this entity's armour spares it the physical part: heavy enchanted armour (players only). */
    public static boolean armourStops(LivingEntity target) {
        if (!(target instanceof PlayerEntity)) return false;
        int worn = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (target.getEquippedStack(slot).getItem() instanceof ArmorItem armour) worn += armour.getProtection();
        }
        // what it wears (its armour attribute lags a tick behind its equipment)
        if (Math.max(worn, target.getAttributeValue(EntityAttributes.GENERIC_ARMOR)) < ARMOUR_THRESHOLD) return false;
        RegistryEntry<Enchantment> protection = target.getRegistryManager()
                .get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.PROTECTION);
        int levels = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armour = target.getEquippedStack(slot);
            levels += EnchantmentHelper.getLevel(protection, armour);
        }
        return levels >= PROTECTION_THRESHOLD;
    }

    /** Whether fire spares this entity (fire-proof, or under fire resistance). */
    public static boolean fireProof(LivingEntity target) {
        return target.isFireImmune() || target.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
    }

    /** The blast's damage on this entity (fire part and physical part, by the rules above). */
    public static float damageOn(LivingEntity target) {
        return (fireProof(target) ? 0 : FIRE_DAMAGE) + (armourStops(target) ? 0 : PHYSICAL_DAMAGE);
    }

    /** Whether this entity holds up a shield toward a jet going along {@code dir}. */
    public static boolean shields(LivingEntity target, Vec3d dir) {
        if (!target.isUsingItem() || target.getActiveItem().getUseAction() != UseAction.BLOCK) return false;
        Vec3d look = target.getRotationVector();
        return look.x * -dir.x + look.z * -dir.z >= SHIELD_FACING * Math.sqrt(dir.x * dir.x + dir.z * dir.z);
    }

    /**
     * Fires a head's jet from {@code from} toward {@code aim}: with lava in the tank the blast (a bucket), else the puff.
     * Returns the entities it hurt or shoved.
     */
    public static List<LivingEntity> fire(TrichaudronEntity trichaudron, int head, Vec3d from, Vec3d aim) {
        List<LivingEntity> hit = new ArrayList<>();
        if (!(trichaudron.getWorld() instanceof ServerWorld world)) return hit;
        boolean full = trichaudron.getTank() > 0;
        double range = full ? RANGE : PUFF_RANGE, radius = full ? RADIUS : PUFF_RADIUS;
        Vec3d dir = aim.subtract(from);
        if (dir.lengthSquared() < 1.0e-6) dir = trichaudron.getRotationVector();
        dir = dir.normalize();
        Vec3d to = from.add(dir.multiply(range));
        BlockHitResult block = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, trichaudron));
        boolean impact = block.getType() != HitResult.Type.MISS;
        double length = impact ? block.getPos().distanceTo(from) : range;
        if (full) trichaudron.setTank(trichaudron.getTank() - 1);

        // who is in the jet, nearest first; a raised shield ends it there
        List<LivingEntity> inJet = new ArrayList<>();
        Box box = new Box(from, from.add(dir.multiply(length))).expand(radius + 1);
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, box, e -> spared(trichaudron, head, e) == null)) {
            if (along(entity, from, dir, length, radius) >= 0) inJet.add(entity);
        }
        Vec3d fromFinal = from, dirFinal = dir;
        inJet.sort((a, b) -> Double.compare(along(a, fromFinal, dirFinal, Double.MAX_VALUE, Double.MAX_VALUE),
                along(b, fromFinal, dirFinal, Double.MAX_VALUE, Double.MAX_VALUE)));
        boolean blocked = false;
        DamageSource steam = new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE)
                .entryOf(TrichaudronEntity.THERMAL_STEAM), trichaudron);
        for (LivingEntity entity : inJet) {
            if (shields(entity, dir)) {
                length = Math.max(0.5, along(entity, from, dir, Double.MAX_VALUE, Double.MAX_VALUE) - entity.getWidth() * 0.5);
                world.playSound(null, entity.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 0.8f);
                blocked = true;
                break;
            }
            if (entity instanceof PlayerEntity player && (player.getAbilities().creativeMode || player.isSpectator())) continue;
            float damage = full ? damageOn(entity) : PUFF_DAMAGE;
            if (damage > 0) entity.damage(steam, damage);
            if (full && !fireProof(entity)) entity.setOnFireFor(FIRE_SECONDS);
            double resist = 1 - entity.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
            if (resist > 0) {
                Vec3d push = new Vec3d(dir.x, 0, dir.z);
                push = push.lengthSquared() < 1.0e-6 ? Vec3d.ZERO : push.normalize();
                double strength = (full ? PUSH : PUFF_PUSH) * resist;
                entity.addVelocity(push.x * strength, (full ? LIFT : PUFF_LIFT) * resist, push.z * strength);
                entity.velocityModified = true;
            }
            hit.add(entity);
        }
        effects(trichaudron, world, from, dir, length, impact || blocked, full);
        return hit;
    }

    /** Why the jet spares this entity, or null if it doesn't. */
    static @Nullable String spared(TrichaudronEntity trichaudron, int head, Entity entity) {
        if (entity == trichaudron || entity instanceof TrichaudronEntity) return "a Trichaudron";
        if (!entity.isAlive() || entity.isSpectator()) return "gone";
        if (trichaudron.hasPassenger(entity)) return "a rider";
        if (entity instanceof PlayerEntity player && trichaudron.trusts(head, player)) return "trusted";
        return null;
    }

    /** How far along the jet this entity's middle is, or -1 if it is out of it. */
    private static double along(LivingEntity entity, Vec3d from, Vec3d dir, double length, double radius) {
        Vec3d center = entity.getBoundingBox().getCenter();
        double t = center.subtract(from).dotProduct(dir);
        if (t < -1 || t > length + 1) return -1;
        double clamped = MathHelper.clamp(t, 0, length);
        double reach = radius + entity.getWidth() * 0.5;
        return from.add(dir.multiply(clamped)).squaredDistanceTo(center) <= reach * reach ? Math.max(0, t) : -1;
    }

    private static void effects(TrichaudronEntity trichaudron, ServerWorld world, Vec3d from, Vec3d dir, double length,
                                boolean impact, boolean full) {
        var random = trichaudron.getRandom();
        trichaudron.playSound(full ? ModSounds.TRICHAUDRON_BLAST : ModSounds.TRICHAUDRON_PUFF, full ? 3.0f : 1.2f, 0.9f + random.nextFloat() * 0.2f);
        double step = full ? 0.8 : 0.6;
        for (double d = 0; d <= length; d += step) {
            Vec3d at = from.add(dir.multiply(d));
            double spread = (full ? 0.5 : 0.3) + d * 0.02;
            Vec3d v = dir.multiply(full ? 0.35 : 0.18);
            world.spawnParticles(ModParticles.THERMAL_PLUME, at.x, at.y, at.z, 0,
                    v.x + random.nextGaussian() * 0.03, v.y + 0.02, v.z + random.nextGaussian() * 0.03, 1.0);
            if (random.nextInt(2) == 0) world.spawnParticles(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, 1, spread, spread, spread, 0.02);
            if (full && d < length * 0.6 && random.nextInt(2) == 0) world.spawnParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0.05);
            if (full && random.nextInt(3) == 0) world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 1, spread, spread, spread, 0.01);
        }
        world.spawnParticles(ModParticles.THERMAL_POOF, from.x, from.y, from.z, full ? 4 : 2, 0.3, 0.3, 0.3, 0.02);
        if (full) world.spawnParticles(ParticleTypes.GUST, from.x, from.y, from.z, 1, 0, 0, 0, 0);
        Vec3d end = from.add(dir.multiply(length));
        world.spawnParticles(ModParticles.THERMAL_POOF, end.x, end.y, end.z, full ? 6 : 2, 0.6, 0.6, 0.6, 0.03);
        if (impact) world.spawnParticles(ParticleTypes.CLOUD, end.x, end.y, end.z, full ? 12 : 4, 0.5, 0.5, 0.5, 0.05);
    }
}
