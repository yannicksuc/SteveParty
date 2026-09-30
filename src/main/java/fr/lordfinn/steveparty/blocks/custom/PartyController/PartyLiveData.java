package fr.lordfinn.steveparty.blocks.custom.PartyController;

import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleNetwork;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.DefaultDiceItem;
import fr.lordfinn.steveparty.service.ShopStops;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the party HUDs show besides the steps ({@link PartyData}): the live state of the current turn and the standings
 * of the party's tokens. Server-authoritative: the party controller captures it a few times per second
 * ({@link #capture}) and sends it to its interested players only when it changed.
 *
 * @param roll          the total rolled for the current turn, 0 while its player has not rolled (or no turn is played)
 * @param stepsLeft     the steps the current turn's token still has to walk (negative: walking back)
 * @param moving        the current turn's token walks, or is about to (a roll was just made)
 * @param shopping      the current turn's token waits at a shop stop while its owner shops
 * @param absentSeconds seconds before the turn of an absent token is skipped, -1 while it is not waited for
 * @param standings     the party's tokens, in the turn order
 */
public record PartyLiveData(int roll, int stepsLeft, boolean moving, boolean shopping, int absentSeconds,
                            List<Standing> standings) {
    public static final PartyLiveData EMPTY = new PartyLiveData(0, 0, false, false, -1, List.of());
    /** Power-ups sent per token at most (the kinds held, the most numerous first). */
    public static final int MAX_POWER_UPS = 6;
    /** Items shown as power-ups in the party HUD (the Double and Triple dice by default): a data pack can add others. */
    public static final TagKey<Item> POWER_UPS = TagKey.of(RegistryKeys.ITEM, Steveparty.id("power_ups"));

    /**
     * A token of the party.
     *
     * @param tokenName its name (its custom name, else the last name known by the party)
     * @param owner     the player playing it, empty for a token anyone may play
     * @param ownerName the owner's name, empty if unknown
     * @param color     its colour (0xRRGGBB: the one given by the Tokenizer Wand, else the colour of its name), -1: none
     * @param online    its owner is connected (always true without owner)
     * @param points    the owner's points on the goal pole bases linked to this party: the ranking criterion
     * @param powerUps  the power-ups the owner holds (one stack per kind, its count the number held)
     */
    public record Standing(UUID token, String tokenName, Optional<UUID> owner, String ownerName, int color,
                           boolean online, int points, List<ItemStack> powerUps) {
        boolean sameAs(Standing other) {
            if (!token.equals(other.token) || !tokenName.equals(other.tokenName) || !owner.equals(other.owner)
                    || !ownerName.equals(other.ownerName) || color != other.color || online != other.online
                    || points != other.points || powerUps.size() != other.powerUps.size())
                return false;
            for (int i = 0; i < powerUps.size(); i++) {
                if (!ItemStack.areEqual(powerUps.get(i), other.powerUps.get(i))) return false;
            }
            return true;
        }
    }

    /** True if nothing shown changed (ItemStack has no value equality, so records can't just be compared). */
    public boolean sameAs(PartyLiveData other) {
        if (other == null || roll != other.roll || stepsLeft != other.stepsLeft || moving != other.moving
                || shopping != other.shopping || absentSeconds != other.absentSeconds
                || standings.size() != other.standings.size())
            return false;
        for (int i = 0; i < standings.size(); i++) {
            if (!standings.get(i).sameAs(other.standings.get(i))) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ capture (server)

    public static PartyLiveData capture(PartyControllerEntity controller, ServerWorld world) {
        PartyData data = controller.getPartyData();
        int roll = 0, stepsLeft = 0, absentSeconds = -1;
        boolean moving = false, shopping = false;
        if (data.getCurrentStep() instanceof TokenTurnPartyStep turn && turn.getStatus() == PartyStep.Status.IN_PROGRESS
                && turn.getTokenUUID() != null) {
            UUID token = turn.getTokenUUID();
            roll = turn.getRoll();
            if (world.getEntity(token) instanceof TokenizedEntityInterface tokenized) stepsLeft = tokenized.steveparty$getNbSteps();
            moving = stepsLeft != 0 || Steveparty.SCHEDULER.isScheduled(token);
            shopping = ShopStops.isShopping(token);
            if (turn.isWaitingForAbsentToken())
                absentSeconds = (int) Math.max(0, (turn.getAbsentDeadline() - world.getTime() + 19) / 20);
        }

        List<GoalPoleBaseBlockEntity> bases = linkedBases(controller, world);
        List<Standing> standings = new ArrayList<>();
        for (UUID token : data.getTokens()) {
            Entity entity = world.getEntity(token);
            UUID owner = entity instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenOwner() : ownerFromTurns(data, token);
            ServerPlayerEntity player = owner == null ? null : world.getServer().getPlayerManager().getPlayer(owner);
            String ownerName = player != null ? player.getNameForScoreboard() : nameOf(world, owner);
            int points = 0;
            if (!ownerName.isEmpty()) {
                for (GoalPoleBaseBlockEntity base : bases) points += base.getPoints(ownerName);
            }
            standings.add(new Standing(token, controller.getTokenDisplayName(world, token).getString(), Optional.ofNullable(owner),
                    ownerName, colorOf(entity), owner == null || player != null, points,
                    player == null ? List.of() : powerUps(player.getInventory())));
        }
        return new PartyLiveData(roll, stepsLeft, moving, shopping, absentSeconds, standings);
    }

    /** The goal pole bases counting for this party: the loaded ones whose nearest party controller is this one. */
    private static List<GoalPoleBaseBlockEntity> linkedBases(PartyControllerEntity controller, ServerWorld world) {
        List<GoalPoleBaseBlockEntity> bases = new ArrayList<>();
        for (GoalPoleBaseBlockEntity base : GoalPoleNetwork.bases()) {
            if (!base.isRemoved() && base.getWorld() == world && base.linkedParty() == controller) bases.add(base);
        }
        return bases;
    }

    private static @Nullable UUID ownerFromTurns(PartyData data, UUID token) {
        for (PartyStep step : data.getSteps()) {
            if (step instanceof TokenTurnPartyStep turn && token.equals(turn.getTokenUUID()) && turn.getOwnerUUID() != null)
                return turn.getOwnerUUID();
        }
        return null;
    }

    private static String nameOf(ServerWorld world, @Nullable UUID player) {
        if (player == null || world.getServer().getUserCache() == null) return "";
        return world.getServer().getUserCache().getByUuid(player).map(GameProfile::getName).orElse("");
    }

    private static int colorOf(@Nullable Entity entity) {
        if (!(entity instanceof TokenizedEntityInterface tokenized)) return -1;
        int color = tokenized.steveparty$getTokenColor();
        if (color >= 0) return color;
        Text name = entity.getCustomName();
        TextColor nameColor = name == null ? null : name.getStyle().getColor();
        return nameColor == null ? -1 : nameColor.getRgb();
    }

    /** A power-up: an item of the {@link #POWER_UPS} tag, or a forged die (a die with faces of its own). */
    public static boolean isPowerUp(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.isIn(POWER_UPS) || (stack.getItem() instanceof DefaultDiceItem && DiceFacesComponent.hasFaces(stack));
    }

    /** The power-ups in an inventory: one stack per kind (same item and components), its count the number held. */
    public static List<ItemStack> powerUps(PlayerInventory inventory) {
        List<ItemStack> kinds = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!isPowerUp(stack)) continue;
            ItemStack kind = null;
            for (ItemStack known : kinds) {
                if (ItemStack.areItemsAndComponentsEqual(known, stack)) {
                    kind = known;
                    break;
                }
            }
            if (kind == null) kinds.add(stack.copy());
            else kind.setCount(kind.getCount() + stack.getCount());
        }
        kinds.sort(Comparator.comparingInt(ItemStack::getCount).reversed());
        return kinds.size() > MAX_POWER_UPS ? List.copyOf(kinds.subList(0, MAX_POWER_UPS)) : kinds;
    }

    /**
     * The rank of each standing (same order), 1 for the best: the most points first; equal points share the rank
     * (1, 1, 3...), like in Mario Party. The turn order breaks nothing: tied tokens keep their order on screen.
     */
    public static int[] ranks(List<Standing> standings) {
        int[] ranks = new int[standings.size()];
        for (int i = 0; i < standings.size(); i++) {
            int better = 0;
            for (Standing other : standings) {
                if (other.points() > standings.get(i).points()) better++;
            }
            ranks[i] = better + 1;
        }
        return ranks;
    }

    // ------------------------------------------------------------------ network

    public static final PacketCodec<RegistryByteBuf, PartyLiveData> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public PartyLiveData decode(RegistryByteBuf buf) {
            int roll = buf.readVarInt();
            int stepsLeft = buf.readVarInt();
            boolean moving = buf.readBoolean();
            boolean shopping = buf.readBoolean();
            int absentSeconds = buf.readVarInt() - 1;
            int count = buf.readVarInt();
            List<Standing> standings = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID token = buf.readUuid();
                String tokenName = buf.readString();
                Optional<UUID> owner = buf.readBoolean() ? Optional.of(buf.readUuid()) : Optional.empty();
                String ownerName = buf.readString();
                int color = buf.readInt();
                boolean online = buf.readBoolean();
                int points = buf.readVarInt();
                int powerUpCount = Math.min(buf.readVarInt(), MAX_POWER_UPS);
                List<ItemStack> powerUps = new ArrayList<>(powerUpCount);
                for (int j = 0; j < powerUpCount; j++) powerUps.add(ItemStack.PACKET_CODEC.decode(buf));
                standings.add(new Standing(token, tokenName, owner, ownerName, color, online, points, powerUps));
            }
            return new PartyLiveData(roll, stepsLeft, moving, shopping, absentSeconds, standings);
        }

        @Override
        public void encode(RegistryByteBuf buf, PartyLiveData data) {
            buf.writeVarInt(data.roll);
            buf.writeVarInt(data.stepsLeft);
            buf.writeBoolean(data.moving);
            buf.writeBoolean(data.shopping);
            buf.writeVarInt(data.absentSeconds + 1);
            buf.writeVarInt(data.standings.size());
            for (Standing standing : data.standings) {
                buf.writeUuid(standing.token);
                buf.writeString(standing.tokenName);
                buf.writeBoolean(standing.owner.isPresent());
                standing.owner.ifPresent(buf::writeUuid);
                buf.writeString(standing.ownerName);
                buf.writeInt(standing.color);
                buf.writeBoolean(standing.online);
                buf.writeVarInt(standing.points);
                List<ItemStack> powerUps = standing.powerUps.size() > MAX_POWER_UPS ? standing.powerUps.subList(0, MAX_POWER_UPS) : standing.powerUps;
                buf.writeVarInt(powerUps.size());
                for (ItemStack stack : powerUps) ItemStack.PACKET_CODEC.encode(buf, stack);
            }
        }
    };
}
