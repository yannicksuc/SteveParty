package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.utils.InventoryUtils;
import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.Steveparty;
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
 * of the party's tokens (their stars and coins: see {@link PartyCurrency}). Server-authoritative: the party controller captures it a few times per second
 * ({@link #capture}) and sends it to its interested players only when it changed.
 *
 * @param roll          the total rolled for the current turn, 0 while its player has not rolled (or no turn is played)
 * @param stepsLeft     the steps the current turn's token still has to walk (negative: walking back)
 * @param moving        the current turn's token walks, or is about to (a roll was just made)
 * @param shopping      the current turn's token waits at a shop stop while its owner shops
 * @param absentSeconds seconds before the turn of an absent token is skipped, -1 while it is not waited for
 * @param starItem      the item counted as stars by this party (see {@link PartyCurrency}), for the HUD's icons
 * @param coinItem      the item counted as coins by this party
 * @param standings     the party's tokens, in the turn order
 * @param effect        what the roll of the current turn does besides steps (coins, swap, a face 0)
 * @param program       before the party's steps exist (the turn order rolls, the preparation): what its program will
 *                      play, for the turn bar's strip of steps; empty afterwards (the steps say it)
 */
public record PartyLiveData(int roll, int stepsLeft, boolean moving, boolean shopping, int absentSeconds,
                            ItemStack starItem, ItemStack coinItem, List<Standing> standings, RollEffect effect,
                            PartyDashboardData.Timeline program) {
    public static final PartyLiveData EMPTY = new PartyLiveData(0, 0, false, false, -1, ItemStack.EMPTY, ItemStack.EMPTY, List.of(), RollEffect.NONE,
            PartyDashboardData.Timeline.EMPTY);
    /** The most steps of the program sent in {@link #program}. */
    public static final int MAX_PROGRAM_STEPS = 32;

    /**
     * What the roll of the current turn does besides walking (see {@code DiceOutcome}).
     *
     * @param rolled   the turn's player rolled something that plays (a roll of 0 included; not the blank side)
     * @param coinFace a coin or debt face was rolled
     * @param coins    the coins gained (negative: lost): what was really given or taken once done
     * @param swap     a swap face was rolled
     * @param swapWith the token it was swapped with, empty until then (the roller is choosing)
     */
    public record RollEffect(boolean rolled, boolean coinFace, int coins, boolean swap, String swapWith) {
        public static final RollEffect NONE = new RollEffect(false, false, 0, false, "");
    }
    /** Power-ups sent per token at most (the kinds held, the most numerous first). */
    public static final int MAX_POWER_UPS = 6;
    /** Items shown as power-ups in the party HUD (the Double and Triple dice by default): a data pack can add others. */
    public static final TagKey<Item> POWER_UPS = TagKey.of(RegistryKeys.ITEM, Steveparty.id("power_ups"));

    /**
     * A token of the party.
     *
     * @param tokenName its pawn's name: the token's own (custom) name, else its player's name, else what it is
     *                  (« Cochon »); an unloaded token: the name the party remembers
     * @param owner     the player playing it, empty for a token anyone may play
     * @param ownerName the owner's name, empty if unknown
     * @param color     its colour (0xRRGGBB: the one given by the Tokenizer Wand, else the colour of its name), -1: none
     * @param online    its owner is connected (always true without owner)
     * @param stars     the party's stars in the owner's inventory: the ranking criterion
     * @param coins     the party's coins in the owner's inventory: the tie-breaker
     * @param powerUps  the power-ups the owner holds (one stack per kind, its count the number held)
     * @param bonuses   the bonuses the token carries (the standings' « bonus » column: {@value #MAX_BONUSES} at most;
     *                  see {@link #bonusesOf})
     */
    public record Standing(UUID token, String tokenName, Optional<UUID> owner, String ownerName, int color,
                           boolean online, int stars, int coins, List<ItemStack> powerUps, List<ItemStack> bonuses) {
        public Standing(UUID token, String tokenName, Optional<UUID> owner, String ownerName, int color,
                        boolean online, int stars, int coins, List<ItemStack> powerUps) {
            this(token, tokenName, owner, ownerName, color, online, stars, coins, powerUps, List.of());
        }

        boolean sameAs(Standing other) {
            if (!token.equals(other.token) || !tokenName.equals(other.tokenName) || !owner.equals(other.owner)
                    || !ownerName.equals(other.ownerName) || color != other.color || online != other.online
                    || stars != other.stars || coins != other.coins || powerUps.size() != other.powerUps.size()
                    || bonuses.size() != other.bonuses.size())
                return false;
            for (int i = 0; i < powerUps.size(); i++) {
                if (!ItemStack.areEqual(powerUps.get(i), other.powerUps.get(i))) return false;
            }
            for (int i = 0; i < bonuses.size(); i++) {
                if (!ItemStack.areEqual(bonuses.get(i), other.bonuses.get(i))) return false;
            }
            return true;
        }
    }

    /** Bonuses sent per token at most (the standings' « bonus » column has as many slots). */
    public static final int MAX_BONUSES = 3;

    /**
     * The bonuses a token carries, shown in the « bonus » column of the standings (one item per bonus, its icon drawn
     * half size in a slot; the column hides itself while nobody has any). The mod has no bonuses yet: none. To feed
     * the column, return here the items standing for the token's bonuses (at most {@value #MAX_BONUSES} are sent);
     * they are captured with the standings and sent when they change, like the stars and coins.
     */
    public static List<ItemStack> bonusesOf(PartyControllerEntity controller, ServerWorld world, UUID token) {
        return List.of();
    }

    /** The roll of the current turn in words: "7", "0", "\u22123" (backward), "+5 coins", "Swap", "3, +2 coins"... */
    public Text rollText() {
        return new fr.lordfinn.steveparty.dice.DiceOutcome(roll, effect.coins(), effect.coinFace(), effect.swap(), false).describe();
    }

    /** True if nothing shown changed (ItemStack has no value equality, so records can't just be compared). */
    public boolean sameAs(PartyLiveData other) {
        if (other == null || roll != other.roll || stepsLeft != other.stepsLeft || moving != other.moving
                || shopping != other.shopping || absentSeconds != other.absentSeconds || !effect.equals(other.effect)
                || !ItemStack.areEqual(starItem, other.starItem) || !ItemStack.areEqual(coinItem, other.coinItem)
                || standings.size() != other.standings.size() || !program.equals(other.program))
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
        RollEffect effect = RollEffect.NONE;
        if (data.getCurrentStep() instanceof TokenTurnPartyStep turn && turn.getStatus() == PartyStep.Status.IN_PROGRESS
                && turn.getTokenUUID() != null) {
            UUID token = turn.getTokenUUID();
            roll = turn.getRoll();
            if (world.getEntity(token) instanceof TokenizedEntityInterface tokenized) stepsLeft = tokenized.steveparty$getNbSteps();
            moving = stepsLeft != 0 || Steveparty.SCHEDULER.isScheduled(token)
                    || fr.lordfinn.steveparty.service.DiceRollEffects.isResolving(token);
            fr.lordfinn.steveparty.dice.DiceOutcome outcome = turn.getOutcome();
            effect = new RollEffect(turn.hasRolled(), outcome.coinFace(), turn.getRollCoins(), outcome.swap(), turn.getSwapWith());
            shopping = ShopStops.isShopping(token);
            if (turn.isWaitingForAbsentToken())
                absentSeconds = (int) Math.max(0, (turn.getAbsentDeadline() - world.getTime() + 19) / 20);
        }

        List<Standing> standings = new ArrayList<>();
        for (UUID token : data.getTokens()) standings.add(standingOf(controller, world, token));
        return new PartyLiveData(roll, stepsLeft, moving, shopping, absentSeconds,
                controller.getCurrency(PartyCurrency.STAR), controller.getCurrency(PartyCurrency.COIN), standings, effect,
                programOf(controller, data));
    }

    /** What the program will play, while the party's steps are not generated yet (see {@link #program}). */
    private static PartyDashboardData.Timeline programOf(PartyControllerEntity controller, PartyData data) {
        PartyStep current = data.getCurrentStep();
        if (current == null || (current.getType() != fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType.START_ROLLS
                && current.getType() != fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType.BASIC_GAME_GENERATOR))
            return PartyDashboardData.Timeline.EMPTY;
        PartyDashboardData.Timeline program = PartyDashboardData.programTimeline(controller.getProgram().getHeldStacks(), data.getNbTurn());
        // Without its first step (the turn order rolls: the current step, or the one just played)
        List<PartyDashboardData.TimelineStep> steps = program.steps();
        int to = Math.min(steps.size(), 1 + MAX_PROGRAM_STEPS);
        if (steps.size() <= 1) return PartyDashboardData.Timeline.EMPTY;
        return new PartyDashboardData.Timeline(List.copyOf(steps.subList(1, to)), 1, -1, program.more() + steps.size() - to);
    }

    /**
     * A token as the party shows it: names, colour, whether its owner is here, and the stars, coins and power-ups in
     * its owner's inventory (none while the owner is disconnected: their inventory can't be read).
     */
    public static Standing standingOf(PartyControllerEntity controller, ServerWorld world, UUID token) {
        Entity entity = world.getEntity(token);
        UUID owner = entity instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenOwner() : ownerFromTurns(controller.getPartyData(), token);
        ServerPlayerEntity player = owner == null ? null : world.getServer().getPlayerManager().getPlayer(owner);
        String ownerName = player != null ? player.getNameForScoreboard() : nameOf(world, owner);
        int stars = 0, coins = 0;
        List<ItemStack> powerUps = List.of();
        if (player != null) {
            stars = InventoryUtils.count(player.getInventory(), controller.getCurrency(PartyCurrency.STAR));
            coins = InventoryUtils.count(player.getInventory(), controller.getCurrency(PartyCurrency.COIN));
            powerUps = powerUps(player.getInventory());
        }
        return new Standing(token, pawnName(controller, world, token, entity, ownerName), Optional.ofNullable(owner),
                ownerName, colorOf(entity), owner == null || player != null, stars, coins, powerUps,
                bonusesOf(controller, world, token));
    }

    /**
     * A pawn's name in the standings: its own (a custom name: the Tokenizer Wand's, a name tag's), else its player's,
     * else what it is (« Cochon »). Unloaded, the name the party remembered of it.
     */
    public static String pawnName(PartyControllerEntity controller, ServerWorld world, UUID token, @Nullable Entity entity, String ownerName) {
        if (entity == null) return controller.getTokenDisplayName(world, token).getString();
        if (entity.getCustomName() != null) return entity.getCustomName().getString();
        return ownerName.isEmpty() ? entity.getName().getString() : ownerName;
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

    /**
     * A power-up: a power-up item ({@code PowerUps}), an item of the {@link #POWER_UPS} tag, or a special die (a die
     * with faces of its own or carrying modules).
     */
    public static boolean isPowerUp(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof fr.lordfinn.steveparty.items.custom.PowerUpItem || stack.isIn(POWER_UPS)
                || (stack.getItem() instanceof DefaultDiceItem
                && (DiceFacesComponent.hasFaces(stack) || !fr.lordfinn.steveparty.dice.DiceModules.of(stack).isEmpty()));
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
     * The rank of each standing (same order), 1 for the best, like in Mario Party: the most stars first, the most coins
     * between equal stars; the same stars and coins share the rank (1, 1, 3...). The turn order breaks nothing: tied
     * tokens keep their order on screen.
     */
    public static int[] ranks(List<Standing> standings) {
        int[] ranks = new int[standings.size()];
        for (int i = 0; i < standings.size(); i++) {
            Standing standing = standings.get(i);
            int better = 0;
            for (Standing other : standings) {
                if (other.stars() > standing.stars() || (other.stars() == standing.stars() && other.coins() > standing.coins()))
                    better++;
            }
            ranks[i] = better + 1;
        }
        return ranks;
    }

    // ------------------------------------------------------------------ network

    public static final PacketCodec<RegistryByteBuf, PartyLiveData> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public PartyLiveData decode(RegistryByteBuf buf) {
            int roll = buf.readInt();
            int stepsLeft = buf.readVarInt();
            boolean moving = buf.readBoolean();
            boolean shopping = buf.readBoolean();
            int absentSeconds = buf.readVarInt() - 1;
            ItemStack starItem = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
            ItemStack coinItem = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
            List<Standing> standings = STANDINGS_CODEC.decode(buf);
            RollEffect effect = new RollEffect(buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readBoolean(), buf.readString());
            return new PartyLiveData(roll, stepsLeft, moving, shopping, absentSeconds, starItem, coinItem, standings, effect,
                    PartyDashboardData.readTimeline(buf));
        }

        @Override
        public void encode(RegistryByteBuf buf, PartyLiveData data) {
            buf.writeInt(data.roll); // negative for a roll going backward
            buf.writeVarInt(data.stepsLeft);
            buf.writeBoolean(data.moving);
            buf.writeBoolean(data.shopping);
            buf.writeVarInt(data.absentSeconds + 1);
            ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, data.starItem);
            ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, data.coinItem);
            STANDINGS_CODEC.encode(buf, data.standings);
            buf.writeBoolean(data.effect.rolled);
            buf.writeBoolean(data.effect.coinFace);
            buf.writeInt(data.effect.coins);
            buf.writeBoolean(data.effect.swap);
            buf.writeString(data.effect.swapWith);
            PartyDashboardData.writeTimeline(buf, data.program);
        }
    };

    /** The standings, also sent by the party controller's dashboard. */
    public static final PacketCodec<RegistryByteBuf, List<Standing>> STANDINGS_CODEC = new PacketCodec<>() {
        @Override
        public List<Standing> decode(RegistryByteBuf buf) {
            int count = buf.readVarInt();
            List<Standing> standings = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID token = buf.readUuid();
                String tokenName = buf.readString();
                Optional<UUID> owner = buf.readBoolean() ? Optional.of(buf.readUuid()) : Optional.empty();
                String ownerName = buf.readString();
                int color = buf.readInt();
                boolean online = buf.readBoolean();
                int stars = buf.readVarInt();
                int coins = buf.readVarInt();
                int powerUpCount = Math.min(buf.readVarInt(), MAX_POWER_UPS);
                List<ItemStack> powerUps = new ArrayList<>(powerUpCount);
                for (int j = 0; j < powerUpCount; j++) powerUps.add(ItemStack.PACKET_CODEC.decode(buf));
                int bonusCount = Math.min(buf.readVarInt(), MAX_BONUSES);
                List<ItemStack> bonuses = new ArrayList<>(bonusCount);
                for (int j = 0; j < bonusCount; j++) bonuses.add(ItemStack.PACKET_CODEC.decode(buf));
                standings.add(new Standing(token, tokenName, owner, ownerName, color, online, stars, coins, powerUps, bonuses));
            }
            return standings;
        }

        @Override
        public void encode(RegistryByteBuf buf, List<Standing> standings) {
            buf.writeVarInt(standings.size());
            for (Standing standing : standings) {
                buf.writeUuid(standing.token);
                buf.writeString(standing.tokenName);
                buf.writeBoolean(standing.owner.isPresent());
                standing.owner.ifPresent(buf::writeUuid);
                buf.writeString(standing.ownerName);
                buf.writeInt(standing.color);
                buf.writeBoolean(standing.online);
                buf.writeVarInt(standing.stars);
                buf.writeVarInt(standing.coins);
                List<ItemStack> powerUps = standing.powerUps.size() > MAX_POWER_UPS ? standing.powerUps.subList(0, MAX_POWER_UPS) : standing.powerUps;
                buf.writeVarInt(powerUps.size());
                for (ItemStack stack : powerUps) ItemStack.PACKET_CODEC.encode(buf, stack);
                List<ItemStack> bonuses = standing.bonuses.size() > MAX_BONUSES ? standing.bonuses.subList(0, MAX_BONUSES) : standing.bonuses;
                buf.writeVarInt(bonuses.size());
                for (ItemStack stack : bonuses) ItemStack.PACKET_CODEC.encode(buf, stack);
            }
        }
    };
}
