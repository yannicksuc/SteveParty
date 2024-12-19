package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpace;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.events.DiceRollEvent;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static fr.lordfinn.steveparty.components.ModComponents.CATALOGUE;
import static fr.lordfinn.steveparty.components.ModComponents.TB_START_OWNER;

public class PartyControllerEntity extends BlockEntity {
    public ItemStack catalogue = ItemStack.EMPTY;
    public long lastTime = 0;
    private final List<UUID> players = new ArrayList<>();

    public PartyControllerEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARTY_CONTROLLER_ENTITY, pos, state);

        DiceRollEvent.EVENT.register((diceEntity, owner, rollValue) -> {
            if (diceEntity.getWorld().isClient) return ActionResult.PASS;
            if (players.contains(owner)) {
            }
            return ActionResult.SUCCESS;
        });
    }

    @Override
    protected void readComponents(ComponentsAccess components) {
        super.readComponents(components);
        this.catalogue = components.getOrDefault(CATALOGUE, ItemStack.EMPTY);
    }

    @Override
    protected void addComponents(ComponentMap.Builder componentMapBuilder) {
        super.addComponents(componentMapBuilder);
        componentMapBuilder.add(CATALOGUE, this.catalogue);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        if (catalogue != null && !catalogue.isEmpty()) {
            NbtElement item = catalogue.toNbt(wrapper, new NbtCompound());
            nbt.put("catalogue", item);
            nbt.putBoolean("isCatalogued", true);
        } else {
            nbt.putBoolean("isCatalogued", false);
        }
        NbtList playerList = new NbtList();
        for (UUID uuid : players) {
            playerList.add(NbtString.of(uuid.toString()));
        }
        nbt.put("players", playerList);
        super.writeNbt(nbt, wrapper);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        NbtElement catalogueElem = nbt.get("catalogue");
        if (catalogueElem != null) {
            Optional<ItemStack> socketedStoryNbt = ItemStack.fromNbt(wrapper, nbt.get("catalogue"));
            socketedStoryNbt.ifPresentOrElse(stack -> catalogue = stack, () -> catalogue = ItemStack.EMPTY);
        }
        boolean isCatalogued = nbt.getBoolean("isCatalogued");
        if (!isCatalogued)
            catalogue = ItemStack.EMPTY;
        players.clear();
        NbtList playerList = nbt.getList("players", NbtElement.STRING_TYPE);
        for (int i = 0; i < playerList.size(); i++) {
            players.add(UUID.fromString(playerList.getString(i)));
        }
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public void markDirty() {
        if (this.world != null && !this.world.isClient && this.world instanceof ServerWorld) {
            this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), 3);
        }
        super.markDirty();
    }

    public boolean setCatalogue(ItemStack itemStack) {
        if (world == null || world.isClient) return !catalogue.isEmpty();

        if (!catalogue.isEmpty()) {
            Entity holder = itemStack.getHolder();
            if (holder instanceof ServerPlayerEntity player) {
                player.giveOrDropStack(catalogue);
            } else {
                ItemScatterer.spawn(world, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, catalogue);
            }
        }
        if (itemStack.isEmpty() || itemStack.getItem() instanceof MiniGamesCatalogueItem)
            catalogue = itemStack.copy();
        this.markDirty();
        return !catalogue.isEmpty();
    }

    public void generateGameSteps() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;

        BlockPos pos = this.getPos();
        List<BlockPos> startTiles = findStartTiles(serverWorld, pos, 100);

        for (BlockPos tilePos : startTiles) {
            BlockEntity tileEntity = serverWorld.getBlockEntity(tilePos);
            if (tileEntity instanceof BoardSpaceEntity tile) {
                String owner = tile.getComponents().get(TB_START_OWNER);
                if (owner != null) {
                    players.add(UUID.fromString(owner));
                }
            }
        }
        markDirty();
    }

    private List<BlockPos> findStartTiles(ServerWorld world, BlockPos center, int radius) {
        List<BlockPos> startTiles = new ArrayList<>();
        Box searchBox = new Box(center.add(-radius, -radius, -radius).toCenterPos(), center.add(radius, radius, radius).toCenterPos());

        for (BlockPos pos : BlockPos.iterate((int) searchBox.minX, (int) searchBox.minY, (int) searchBox.minZ,
                (int) searchBox.maxX, (int) searchBox.maxY, (int) searchBox.maxZ)) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BoardSpace && state.get(BoardSpace.TILE_TYPE) == BoardSpaceType.TILE_START) {
                startTiles.add(pos);
            }
        }
        return startTiles;
    }
}
