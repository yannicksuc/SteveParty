package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.Tile;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.particles.ParticleUtils;
import fr.lordfinn.steveparty.payloads.ArrowParticlesPayload;
import fr.lordfinn.steveparty.service.TokenMovementService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.*;
import java.util.List;

import static fr.lordfinn.steveparty.entities.ModEntities.DIRECTION_DISPLAY_ENTITY;
import static fr.lordfinn.steveparty.utils.QuaternionsUtils.dirToYAngle;
import static net.minecraft.entity.attribute.EntityAttributes.ENTITY_INTERACTION_RANGE;

public class DirectionDisplayEntity extends DisplayEntity.BlockDisplayEntity {
    private Vec3d encodedVelocity;
    private Vec3d start;
    private ServerPlayerEntity owner;
    private BlockPos tileOrigin;
    private BoardSpaceDestination tileDestination;
    private static final long DISPLAY_INTERVAL = 15;



    public DirectionDisplayEntity(EntityType<DirectionDisplayEntity> directionDisplayEntityEntityType, World world) {
        super(directionDisplayEntityEntityType, world);
    }

    public DirectionDisplayEntity(World world, BoardSpaceDestination destination, BlockPos origin, ServerPlayerEntity owner) {
        super(DIRECTION_DISPLAY_ENTITY, world);
        this.owner = owner;
        this.tileOrigin = origin;
        this.tileDestination = destination;
        BlockPos distanceAsBlockPos = destination.position().subtract(origin);
        Vec3d distance = new Vec3d(distanceAsBlockPos.getX(), distanceAsBlockPos.getY(), distanceAsBlockPos.getZ());
        Color color = destination.isTile() ? Color.WHITE : Color.RED;
        encodedVelocity = ParticleUtils.encodeVelocity(
                color,
                (float) distance.x,
                (float) distance.y,
                (float) distance.z);
        start = origin.toCenterPos().add(0,0.1,0);
        float size = 0.3F;
        Vec3d startGap = distance.normalize().multiply(Math.min(1.5, distance.length())).add(0,-0.4,0);
        this.setPosition(start.add(startGap));
        BlockState blockState = world.getBlockState(destination.position());
        if (blockState != null && blockState.getBlock() instanceof Tile) {
            blockState = blockState.with(Properties.HORIZONTAL_FACING, Direction.SOUTH);
        }
        this.setBlockState(blockState);
        Quaternionf rot = new Quaternionf();
        rot.rotateLocalZ((float) Math.toRadians(90f))
           .rotateLocalY((float) (dirToYAngle(-distance.getX(), -distance.getZ()) + Math.PI / 2));
        AffineTransformation scaleTransformation = new AffineTransformation(new Vector3f(-0.15f, 0, -0.65f), null,
                new Vector3f(size), null);
        this.setTransformation(scaleTransformation);
        this.setBillboardMode(BillboardMode.VERTICAL);
        this.rotate(0, (float) Math.toDegrees((float) Math.toRadians(90f)));
        world.spawnEntity(this);
    }

    public static NbtElement Vec3dToNbt(Vec3d vec) {
        NbtCompound nbt = new NbtCompound();
        nbt.putDouble("x", vec.x);
        nbt.putDouble("y", vec.y);
        nbt.putDouble("z", vec.z);
        return nbt;
    }

    public static Vec3d Vec3dFromNbt(NbtCompound nbt) {
        return new Vec3d(nbt.getDouble("x"), nbt.getDouble("y"), nbt.getDouble("z"));
    }

    private boolean isPlayerLookingAt(ServerPlayerEntity player, DirectionDisplayEntity entity) {
        if (!player.getActiveItem().isEmpty()) return false;
        double maxDistance = player.getAttributeValue(ENTITY_INTERACTION_RANGE); // Distance maximale pour le raycast
        Vec3d start = player.getEyePos(); // Position des yeux du joueur
        Vec3d direction = player.getRotationVec(1.0F); // Direction du regard du joueur
        Vec3d end = start.add(direction.multiply(maxDistance)); // Fin du rayon (distance maximale)

        // Effectuer le raycast
        Box entityBox = entity.getBoundingBox(); // Boîte englobante de l'entité
        Vec3d hit = entityBox.raycast(start, end).orElse(null); // Tester si le rayon frappe la boîte
        return hit != null; // Si le rayon frappe la boîte de l'entité, elle est visée
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (owner != null) {
            nbt.putUuid("Owner", owner.getUuid());
        }
        if (tileDestination != null) {
            nbt.put("TileDestination", tileDestination.toNbt());
        }
        if (encodedVelocity != null) {
            nbt.put("EncodedVelocity", Vec3dToNbt(encodedVelocity));
        }
        if (start != null) {
            nbt.put("Start", Vec3dToNbt(start));
        }
        if (tileOrigin != null) {
            nbt.put("tileOrigin", NbtHelper.fromBlockPos(tileOrigin));
        }
    }



    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Owner")) {
            MinecraftServer server = this.getWorld().getServer();
            if (server == null) return;
            owner = server.getPlayerManager().getPlayer(nbt.getUuid("Owner"));
        }
        if (nbt.contains("TileDestination")) {
            tileDestination = BoardSpaceDestination.fromNbt(nbt.getCompound("TileDestination"));
        }
        if (nbt.contains("EncodedVelocity")) {
            encodedVelocity = Vec3dFromNbt(nbt.getCompound("EncodedVelocity"));
        }
        if (nbt.contains("Start")) {
            start = Vec3dFromNbt(nbt.getCompound("Start"));
        }
        if (nbt.contains("tileOrigin")) {
            NbtHelper.toBlockPos(nbt, "tileOrigin").ifPresent(this::setTileOrigin);
        }
    }

    private void setTileOrigin(BlockPos blockPos) {
        this.tileOrigin = blockPos;
        writeCustomDataToNbt(new NbtCompound());
    }

    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        if (owner != null) {
            boolean isLookingAt = isPlayerLookingAt(owner, this);
            this.setGlowing(isLookingAt);
        }

        if (getWorld().getTime() % DISPLAY_INTERVAL == 0) {
            ((ServerWorld) getWorld()).getPlayers().stream()
                    .filter(player -> isWithinDistance(BlockPos.ofFloored(start), player))
                    .forEach(this::renderDirection);
        }
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient && hand.equals(Hand.MAIN_HAND) && player.getMainHandStack().isEmpty()) {
            TokenMovementService.moveEntityOnTileToDestination((ServerWorld) this.getWorld(), tileOrigin, tileDestination);
            this.discard();
            return ActionResult.SUCCESS; // Indicate the interaction was successful
        }
        return super.interact(player, hand); // Default interaction handling
    }

    @Override
    public boolean canHit() {
        return true;
    }

    private static boolean isWithinDistance(BlockPos origin, PlayerEntity player) {
        return player.getBlockPos().isWithinDistance(origin.toCenterPos(), 15);
    }

    private void renderDirection(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new ArrowParticlesPayload(start, encodedVelocity));
    }

    public BlockPos getTileOrigin() {
        return tileOrigin;
    }
}
