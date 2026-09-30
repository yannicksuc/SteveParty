package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.StartRollsStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the party HUDs show, worked out once each time the party data changes (never per frame): the players in the
 * turn order, the round, the current turn and what is happening now.
 */
final class PartyHudModel {
    static final class Player {
        UUID token;
        String name = "";
        @Nullable UUID owner;
        String ownerName = "";
        /** Opaque ARGB. */
        int color;
        boolean online = true;
        /** Played by the player of this client. */
        boolean mine;
        /** The party's stars and coins its player holds. */
        int stars;
        int coins;
        List<ItemStack> powerUps = List.of();
        int rank = 1;
        /** Its turn of the current round is over. */
        boolean played;
        /** Its roll for the turn order (start rolls), 0 if not rolled. */
        int startRoll;
    }

    /** What the action line says. */
    enum Action { NONE, START_ROLLS, PREPARING, MINI_GAME, ROLL, ROLLED, MOVING, SHOPPING, ABSENT, OTHER }

    final List<Player> players = new ArrayList<>();
    /** Index in {@link #players} of the token playing its turn, -1 when no token turn is played. */
    int current = -1;
    boolean replay;
    PartyStepType stepType = PartyStepType.DEFAULT;
    /** 1-based round, 0 before the first one (turn order rolls, preparation). */
    int round;
    /** Number of rounds of the party, 0 if unknown. */
    int rounds;
    Action action = Action.NONE;
    Text actionText = Text.empty();
    Identifier actionIcon = HudDraw.ICON_DICE;
    /** The number shown next to the action (steps left, roll, seconds...), null for none. */
    @Nullable String badge;
    boolean warn;
    /** The action concerns the player of this client (their turn to roll, to shop...). */
    boolean yourTurn;
    /** Identity of the action line: a new one cross-fades, the same one with a new badge only pops the badge. */
    int actionKey;
    /** Whether someone holds stars or coins (else they're all 0, not a ranking yet). */
    boolean hasStandings;
    /** The items this party counts as stars and coins (their icons in the standings). */
    ItemStack starItem = ItemStack.EMPTY;
    ItemStack coinItem = ItemStack.EMPTY;

    static PartyHudModel build(PartyData data, PartyLiveData live, @Nullable UUID me) {
        PartyHudModel model = new PartyHudModel();
        List<PartyStep> steps = data.getSteps();
        int stepIndex = data.getStepIndex();
        PartyStep currentStep = stepIndex >= 0 && stepIndex < steps.size() ? steps.get(stepIndex) : null;
        model.stepType = currentStep == null ? PartyStepType.DEFAULT : currentStep.getType();

        // Players, in the turn order
        for (UUID token : data.getTokens()) {
            Player player = new Player();
            player.token = token;
            PartyLiveData.Standing standing = standing(live, token);
            TokenTurnPartyStep turn = firstTurn(steps, token);
            if (standing != null) {
                player.name = standing.tokenName();
                player.owner = standing.owner().orElse(null);
                player.ownerName = standing.ownerName();
                player.online = standing.online();
                player.stars = standing.stars();
                player.coins = standing.coins();
                player.powerUps = standing.powerUps();
            } else if (turn != null) {
                player.name = turn.getTokenDisplayName(null).getString();
                player.owner = turn.getOwnerUUID();
            }
            if (player.ownerName.isEmpty() && player.owner != null) player.ownerName = listedName(player.owner);
            if (player.name.isEmpty()) player.name = player.ownerName.isEmpty() ? token.toString().substring(0, 8) : player.ownerName;
            player.color = HudDraw.tokenColor(standing != null ? standing.color() : -1, model.players.size());
            player.mine = me != null && me.equals(player.owner);
            model.players.add(player);
        }
        model.starItem = live.starItem();
        model.coinItem = live.coinItem();
        // A ranking once someone holds stars or coins: before, everybody would be « 1st »
        model.hasStandings = live.standings().stream().anyMatch(standing -> standing.stars() != 0 || standing.coins() != 0);
        if (model.hasStandings) {
            List<PartyLiveData.Standing> standings = new ArrayList<>();
            for (Player player : model.players) {
                PartyLiveData.Standing standing = standing(live, player.token);
                standings.add(standing != null ? standing : new PartyLiveData.Standing(player.token, "", java.util.Optional.empty(), "", -1, true, player.stars, player.coins, List.of()));
            }
            int[] ranks = PartyLiveData.ranks(standings);
            for (int i = 0; i < ranks.length; i++) model.players.get(i).rank = ranks[i];
        }

        // Rounds: a round is the token turns up to a mini-game
        int roundStart = 0;
        for (int i = 0; i < steps.size(); i++) {
            PartyStepType type = steps.get(i).getType();
            if (type == PartyStepType.MINI_GAME) {
                model.rounds++;
                if (i < stepIndex) {
                    model.round++;
                    roundStart = i + 1;
                }
            } else if (type == PartyStepType.BASIC_GAME_GENERATOR && i < stepIndex) {
                roundStart = i + 1;
            }
        }
        boolean beforeRounds = model.stepType == PartyStepType.START_ROLLS || model.stepType == PartyStepType.BASIC_GAME_GENERATOR;
        model.round = beforeRounds ? 0 : Math.min(model.round + 1, Math.max(model.rounds, 1));
        for (int i = roundStart; i < stepIndex && i < steps.size(); i++) {
            if (steps.get(i) instanceof TokenTurnPartyStep turn) {
                Player player = find(model, turn.getTokenUUID());
                if (player != null) player.played = true;
            }
        }
        if (model.stepType == PartyStepType.MINI_GAME) model.players.forEach(player -> player.played = true);

        // The current step
        if (currentStep instanceof TokenTurnPartyStep turn) {
            model.current = indexOf(model, turn.getTokenUUID());
            model.replay = turn.isReplay();
            describeTurn(model, live, model.current >= 0 ? model.players.get(model.current) : null);
        } else if (currentStep instanceof StartRollsStep startRolls) {
            int rolled = 0;
            boolean waitingForMe = false;
            for (Player player : model.players) {
                Integer roll = startRolls.rolls == null ? null : startRolls.rolls.get(player.token);
                player.startRoll = roll == null ? 0 : roll;
                if (roll != null) rolled++;
                else if (player.mine) waitingForMe = true;
            }
            model.yourTurn = waitingForMe;
            model.set(Action.START_ROLLS, HudDraw.ICON_DICE,
                    Text.translatable(waitingForMe ? "hud.steveparty.party.start_rolls.you" : "hud.steveparty.party.start_rolls"));
            model.badge = rolled + "/" + model.players.size();
        } else if (model.stepType == PartyStepType.BASIC_GAME_GENERATOR) {
            model.set(Action.PREPARING, HudDraw.ICON_PREPARING, Text.translatable("hud.steveparty.party.preparing"));
        } else if (currentStep instanceof MiniGamePartyStep miniGame) {
            model.set(Action.MINI_GAME, HudDraw.ICON_MINI_GAME, Text.translatable(miniGame.isMiniGameChosen()
                    ? "hud.steveparty.party.mini_game" : "hud.steveparty.party.mini_game.choosing"));
        } else if (currentStep != null) {
            model.set(Action.OTHER, HudDraw.ICON_PREPARING, Text.translatable(currentStep.getName()));
        }
        model.actionKey = java.util.Objects.hash(stepIndex, model.action, model.current, model.yourTurn);
        return model;
    }

    private static void describeTurn(PartyHudModel model, PartyLiveData live, @Nullable Player player) {
        String name = player == null ? "?" : player.name;
        String owner = player == null || player.ownerName.isEmpty() ? name : player.ownerName;
        boolean mine = player != null && player.mine;
        if (live.absentSeconds() >= 0) {
            model.set(Action.ABSENT, HudDraw.ICON_CLOCK, Text.translatable("hud.steveparty.party.absent", name));
            model.badge = Text.translatable("hud.steveparty.party.seconds", live.absentSeconds()).getString();
            model.warn = true;
        } else if (live.shopping()) {
            model.yourTurn = mine;
            model.set(Action.SHOPPING, HudDraw.ICON_SHOP, mine
                    ? Text.translatable("hud.steveparty.party.shopping.you") : Text.translatable("hud.steveparty.party.shopping", owner));
        } else if (live.stepsLeft() != 0) {
            int steps = Math.abs(live.stepsLeft());
            model.set(Action.MOVING, HudDraw.ICON_STEPS, Text.translatable(live.stepsLeft() > 0
                    ? "hud.steveparty.party.moving" : "hud.steveparty.party.moving_back"));
            model.badge = Text.translatable(steps == 1 ? "hud.steveparty.party.steps.one" : "hud.steveparty.party.steps", steps).getString();
        } else if (live.roll() > 0) {
            model.set(Action.ROLLED, HudDraw.ICON_DICE, mine ? Text.translatable("hud.steveparty.party.rolled.you") : Text.translatable("hud.steveparty.party.rolled", owner));
            model.badge = Integer.toString(live.roll());
        } else {
            model.yourTurn = mine;
            Text text;
            if (mine) text = Text.translatable("hud.steveparty.party.roll.you");
            else if (player != null && player.owner == null) text = Text.translatable("hud.steveparty.party.roll.anyone", name);
            else text = Text.translatable("hud.steveparty.party.roll", owner);
            model.set(Action.ROLL, HudDraw.ICON_DICE, text);
        }
    }

    private void set(Action action, Identifier icon, Text text) {
        this.action = action;
        this.actionIcon = icon;
        this.actionText = text;
    }

    private static @Nullable PartyLiveData.Standing standing(PartyLiveData live, UUID token) {
        for (PartyLiveData.Standing standing : live.standings()) {
            if (standing.token().equals(token)) return standing;
        }
        return null;
    }

    private static @Nullable TokenTurnPartyStep firstTurn(List<PartyStep> steps, UUID token) {
        for (PartyStep step : steps) {
            if (step instanceof TokenTurnPartyStep turn && token.equals(turn.getTokenUUID())) return turn;
        }
        return null;
    }

    private static @Nullable Player find(PartyHudModel model, @Nullable UUID token) {
        int index = indexOf(model, token);
        return index < 0 ? null : model.players.get(index);
    }

    private static int indexOf(PartyHudModel model, @Nullable UUID token) {
        if (token == null) return -1;
        for (int i = 0; i < model.players.size(); i++) {
            if (model.players.get(i).token.equals(token)) return i;
        }
        return -1;
    }

    private static String listedName(UUID player) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        PlayerListEntry entry = handler == null ? null : handler.getPlayerListEntry(player);
        return entry == null ? "" : entry.getProfile().getName();
    }

    // ------------------------------------------------------------------ preview

    /** A made-up party for the layout screen when no party is running: three players, the second one moving. */
    static PartyHudModel sample(@Nullable UUID me, String myName) {
        PartyHudModel model = new PartyHudModel();
        String[][] names = {{"Cochonou", "Alex"}, {"Meuh", myName}, {"Bêêê", "Steve"}};
        int[] stars = {1, 2, 1};
        int[] coins = {12, 8, 5};
        model.starItem = PartyCurrency.STAR.defaultStack();
        model.coinItem = PartyCurrency.COIN.defaultStack();
        for (int i = 0; i < names.length; i++) {
            Player player = new Player();
            player.token = new UUID(0x5A3B1EL, i);
            player.name = names[i][0];
            player.ownerName = names[i][1];
            player.owner = i == 1 ? me : new UUID(0x5A3B1EL, 100 + i);
            player.mine = i == 1 && me != null;
            player.color = HudDraw.tokenColor(-1, i);
            player.stars = stars[i];
            player.coins = coins[i];
            player.played = i == 0;
            model.players.add(player);
        }
        model.players.get(1).powerUps = List.of(new ItemStack(ModItems.DOUBLE_DICE, 2), new ItemStack(ModItems.TRIPLE_DICE));
        model.players.get(0).powerUps = List.of(new ItemStack(ModItems.DOUBLE_DICE));
        model.players.get(0).rank = 2;
        model.players.get(1).rank = 1;
        model.players.get(2).rank = 3;
        model.hasStandings = true;
        model.stepType = PartyStepType.TOKEN_TURN;
        model.current = 1;
        model.round = 3;
        model.rounds = 10;
        model.set(Action.MOVING, HudDraw.ICON_STEPS, Text.translatable("hud.steveparty.party.moving"));
        model.badge = Text.translatable("hud.steveparty.party.steps", 4).getString();
        model.actionKey = 1;
        return model;
    }
}
