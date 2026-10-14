package fr.lordfinn.steveparty.blocks.custom.PartyController.journal;

import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Where the turn journal saw items go from or to: a player, a party's bank, a cartridge's containers, a Magpie Nest, an
 * item fallen in the world, or nowhere (used up). Saved by what finds it again, never by what it held: the journal
 * takes back from it what is there <em>when it undoes</em> (see {@link JournalRewind}).
 */
public sealed interface JournalPlace {
    /** Nowhere: used up, or a source it does not know. Nothing is ever taken from it, nothing goes back to it. */
    JournalPlace GONE = new Gone();

    NbtCompound toNbt();

    /** It as it stands now, null when it can't be reached (offline, unloaded, gone): nothing taken from it, nothing given. */
    @Nullable Live live(MinecraftServer server);

    /** What the chat calls it. */
    Text name(MinecraftServer server);

    static JournalPlace player(UUID id) {
        return new Player(id);
    }

    static JournalPlace player(ServerPlayerEntity player) {
        return new Player(player.getUuid());
    }

    /** The place of {@code source}, {@link #GONE} when it has none. */
    static JournalPlace of(PartyResources source) {
        JournalPlace place = source.place();
        return place == null ? GONE : place;
    }

    /** An item entity fallen in the world (what did not fit somewhere): taken back while it lies there. */
    static JournalPlace dropped(ItemEntity item) {
        return new Dropped(item.getUuid(), item.getWorld().getRegistryKey());
    }

    /** A place as it stands now: how much it holds or takes of an item, and taking or putting it. */
    interface Live {
        int available(ItemStack template);

        /** How many more it takes ({@link Integer#MAX_VALUE}: no end, a player whose overflow falls at his feet). */
        int room(ItemStack template);

        /** @return how many came out */
        int take(ItemStack template, int count);

        /** @return how many went in (never more than {@link #room}) */
        int put(ItemStack template, int count);
    }

    record Gone() implements JournalPlace {
        @Override
        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("Kind", "gone");
            return nbt;
        }

        @Override
        public @Nullable Live live(MinecraftServer server) {
            return null;
        }

        @Override
        public Text name(MinecraftServer server) {
            return Text.translatable("journal.steveparty.place.gone");
        }
    }

    /** A player's inventory: online and not holding a mini-game session inventory, or out of reach. */
    record Player(UUID id) implements JournalPlace {
        @Override
        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("Kind", "player");
            nbt.putUuid("Id", id);
            return nbt;
        }

        @Override
        public @Nullable Live live(MinecraftServer server) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player == null || player.isRemoved() || ZoneBubbles.ofPlayer(player) != null) return null;
            return new Live() {
                @Override
                public int available(ItemStack template) {
                    return InventoryUtils.count(player.getInventory(), template);
                }

                @Override
                public int room(ItemStack template) {
                    return Integer.MAX_VALUE;
                }

                @Override
                public int take(ItemStack template, int count) {
                    return InventoryUtils.take(player.getInventory(), template, count);
                }

                @Override
                public int put(ItemStack template, int count) {
                    InventoryUtils.giveOrDrop(player, template, count);
                    return count;
                }
            };
        }

        @Override
        public Text name(MinecraftServer server) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) return player.getDisplayName();
            return server.getUserCache() == null ? Text.literal(id.toString().substring(0, 8))
                    : server.getUserCache().getByUuid(id).map(profile -> (Text) Text.literal(profile.getName()))
                    .orElse(Text.literal(id.toString().substring(0, 8)));
        }
    }

    /** The bank of the Party Controller at {@code controller}, as {@link PartyResources#of(PartyControllerEntity)} gives it then. */
    record Bank(GlobalPos controller) implements JournalPlace {
        @Override
        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("Kind", "bank");
            putPos(nbt, controller);
            return nbt;
        }

        @Override
        public @Nullable Live live(MinecraftServer server) {
            ServerWorld world = loaded(server, controller);
            if (world == null || !(world.getBlockEntity(controller.pos()) instanceof PartyControllerEntity party)) return null;
            return resources(PartyResources.of(party));
        }

        @Override
        public Text name(MinecraftServer server) {
            return Text.translatable("journal.steveparty.place.bank");
        }
    }

    /** The containers of a cartridge (or of a bank cartridge read alone), in their order. */
    record Containers(List<GlobalPos> positions) implements JournalPlace {
        /** The place of {@code inventories} when each is a block's, null otherwise. */
        public static @Nullable Containers of(List<Inventory> inventories) {
            List<GlobalPos> positions = new ArrayList<>();
            for (Inventory inventory : inventories) {
                if (!(inventory instanceof BlockEntity blockEntity) || blockEntity.getWorld() == null) return null;
                positions.add(GlobalPos.create(blockEntity.getWorld().getRegistryKey(), blockEntity.getPos()));
            }
            return positions.isEmpty() ? null : new Containers(List.copyOf(positions));
        }

        @Override
        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("Kind", "containers");
            NbtList list = new NbtList();
            for (GlobalPos pos : positions) {
                NbtCompound entry = new NbtCompound();
                putPos(entry, pos);
                list.add(entry);
            }
            nbt.put("Positions", list);
            return nbt;
        }

        @Override
        public @Nullable Live live(MinecraftServer server) {
            List<Inventory> inventories = new ArrayList<>();
            for (GlobalPos pos : positions) {
                ServerWorld world = loaded(server, pos);
                if (world != null && world.getBlockEntity(pos.pos()) instanceof Inventory inventory) inventories.add(inventory);
            }
            return inventories.isEmpty() ? null : resources(PartyResources.of(inventories));
        }

        @Override
        public Text name(MinecraftServer server) {
            BlockPos pos = positions.getFirst().pos();
            return Text.translatable("journal.steveparty.place.chest", pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /** A Magpie Nest (a Common pot's). */
    record Nest(GlobalPos pos) implements JournalPlace {
        public static Nest of(MagpieNestBlockEntity nest) {
            return new Nest(GlobalPos.create(nest.getWorld().getRegistryKey(), nest.getPos()));
        }

        @Override
        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("Kind", "nest");
            putPos(nbt, pos);
            return nbt;
        }

        @Override
        public @Nullable Live live(MinecraftServer server) {
            ServerWorld world = loaded(server, pos);
            if (world == null || !(world.getBlockEntity(pos.pos()) instanceof MagpieNestBlockEntity nest)) return null;
            return new Live() {
                @Override
                public int available(ItemStack template) {
                    return nest.count(template);
                }

                @Override
                public int room(ItemStack template) {
                    return nest.room(template);
                }

                @Override
                public int take(ItemStack template, int count) {
                    return nest.take(template, count);
                }

                @Override
                public int put(ItemStack template, int count) {
                    return nest.putBack(template, count);
                }
            };
        }

        @Override
        public Text name(MinecraftServer server) {
            return Text.translatable("journal.steveparty.place.nest");
        }
    }

    /** An item entity in the world: taken back from while it lies there; nothing is put back in it. */
    record Dropped(UUID entity, RegistryKey<World> dimension) implements JournalPlace {
        @Override
        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("Kind", "dropped");
            nbt.putUuid("Id", entity);
            nbt.putString("Dimension", dimension.getValue().toString());
            return nbt;
        }

        @Override
        public @Nullable Live live(MinecraftServer server) {
            ServerWorld world = server.getWorld(dimension);
            if (world == null || !(world.getEntity(entity) instanceof ItemEntity item) || !item.isAlive()) return null;
            return new Live() {
                @Override
                public int available(ItemStack template) {
                    ItemStack stack = item.getStack();
                    return ItemStack.areItemsAndComponentsEqual(stack, template) ? stack.getCount() : 0;
                }

                @Override
                public int room(ItemStack template) {
                    return 0;
                }

                @Override
                public int take(ItemStack template, int count) {
                    ItemStack stack = item.getStack().copy();
                    if (!ItemStack.areItemsAndComponentsEqual(stack, template)) return 0;
                    int taken = Math.min(count, stack.getCount());
                    stack.decrement(taken);
                    if (stack.isEmpty()) item.discard();
                    else item.setStack(stack);
                    return taken;
                }

                @Override
                public int put(ItemStack template, int count) {
                    return 0;
                }
            };
        }

        @Override
        public Text name(MinecraftServer server) {
            return Text.translatable("journal.steveparty.place.ground");
        }
    }

    static JournalPlace fromNbt(NbtCompound nbt) {
        return switch (nbt.getString("Kind")) {
            case "player" -> nbt.containsUuid("Id") ? new Player(nbt.getUuid("Id")) : GONE;
            case "bank" -> {
                GlobalPos pos = readPos(nbt);
                yield pos == null ? GONE : new Bank(pos);
            }
            case "containers" -> {
                List<GlobalPos> positions = new ArrayList<>();
                for (NbtElement element : nbt.getList("Positions", NbtElement.COMPOUND_TYPE)) {
                    GlobalPos pos = readPos((NbtCompound) element);
                    if (pos != null) positions.add(pos);
                }
                yield positions.isEmpty() ? GONE : new Containers(List.copyOf(positions));
            }
            case "nest" -> {
                GlobalPos pos = readPos(nbt);
                yield pos == null ? GONE : new Nest(pos);
            }
            case "dropped" -> {
                Identifier dimension = Identifier.tryParse(nbt.getString("Dimension"));
                yield dimension == null || !nbt.containsUuid("Id") ? GONE
                        : new Dropped(nbt.getUuid("Id"), RegistryKey.of(RegistryKeys.WORLD, dimension));
            }
            default -> GONE;
        };
    }

    private static Live resources(PartyResources resources) {
        return new Live() {
            @Override
            public int available(ItemStack template) {
                return resources.available(template);
            }

            @Override
            public int room(ItemStack template) {
                return resources.room(template);
            }

            @Override
            public int take(ItemStack template, int count) {
                return resources.take(template, count);
            }

            @Override
            public int put(ItemStack template, int count) {
                int put = 0;
                int left = Math.min(count, resources.room(template));
                while (left > 0) {
                    ItemStack stack = template.copyWithCount(Math.min(left, template.getMaxCount()));
                    int size = stack.getCount();
                    int in = resources.give(stack);
                    put += in;
                    left -= size;
                    if (in < size) break;
                }
                return put;
            }
        };
    }

    /** The world of {@code pos} when its chunk is loaded (nothing is loaded for an undo), null otherwise. */
    @SuppressWarnings("deprecation")
    private static @Nullable ServerWorld loaded(MinecraftServer server, GlobalPos pos) {
        ServerWorld world = server.getWorld(pos.dimension());
        return world != null && world.isChunkLoaded(pos.pos()) ? world : null;
    }

    private static void putPos(NbtCompound nbt, GlobalPos pos) {
        nbt.putString("Dimension", pos.dimension().getValue().toString());
        nbt.putLong("Pos", pos.pos().asLong());
    }

    private static @Nullable GlobalPos readPos(NbtCompound nbt) {
        Identifier dimension = Identifier.tryParse(nbt.getString("Dimension"));
        if (dimension == null || !nbt.contains("Pos")) return null;
        return GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension), BlockPos.fromLong(nbt.getLong("Pos")));
    }
}
