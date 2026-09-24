package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BasicGameGeneratorStep extends PartyStep {
    private UUID startTaskId = null;

    public BasicGameGeneratorStep(NbtCompound nbt) {
        super(nbt);
    }
    public BasicGameGeneratorStep() {
        super();
        setType(PartyStepType.BASIC_GAME_GENERATOR);
    }

    @Override
    public void start(PartyControllerEntity partyControllerEntity) {
        if (partyControllerEntity == null || partyControllerEntity.getWorld() == null || partyControllerEntity.getWorld().isClient) return;
        super.start(partyControllerEntity);
        if (partyControllerEntity.getPartyData().getTokens().isEmpty()) {
            partyControllerEntity.nextStep();
            return;
        }
        ServerWorld world = (ServerWorld) partyControllerEntity.getWorld();
        if (world == null) return;
        PartyData partyData = partyControllerEntity.getPartyData();
        generateSteps(partyData, world, partyControllerEntity.getProgram());
        partyControllerEntity.markDirty();
        scheduleStart(partyControllerEntity, world);
    }

    @Override
    public void resume(PartyControllerEntity partyControllerEntity) {
        // The steps were generated (and saved) by start(), only the delayed transition was lost
        if (partyControllerEntity.getWorld() instanceof ServerWorld world)
            scheduleStart(partyControllerEntity, world);
    }

    @Override
    public void end(PartyControllerEntity partyControllerEntity) {
        super.end(partyControllerEntity);
        if (startTaskId != null) {
            Steveparty.SCHEDULER.cancel(startTaskId);
            startTaskId = null;
        }
    }

    private void scheduleStart(PartyControllerEntity partyControllerEntity, ServerWorld world) {
        if (startTaskId != null) Steveparty.SCHEDULER.cancel(startTaskId);
        startTaskId = UUID.randomUUID();
        Steveparty.SCHEDULER.schedule(startTaskId, 20,
            () -> {
                startTaskId = null;
                if (!isStillActive(partyControllerEntity)) return;
                PartyData partyData = partyControllerEntity.getPartyData();
                MessageUtils.sendToPlayers(partyData.getOwners(world), Text.translatable("message.steveparty.basic_game_start"), MessageUtils.MessageType.CHAT);
                partyControllerEntity.nextStep();
            }
        );
    }

    private void generateSteps(PartyData partyData, ServerWorld world, List<ItemStack> program) {
        List<UUID> tokens = partyData.getTokens(); // Assuming this method retrieves the list of tokens
        if (tokens.isEmpty()) return;

        // Idempotent: drop what a previous run of this generator produced (restart / previous step)
        List<PartyStep> steps = partyData.getSteps();
        int generatorIndex = steps.indexOf(this);
        if (generatorIndex >= 0 && generatorIndex + 1 < steps.size())
            steps.subList(generatorIndex + 1, steps.size()).clear();

        for (ExpandedCard card : expand(program, partyData.getNbTurn())) {
            switch (card.type()) {
                case TURNS -> {
                    // Token turn steps: every registered token gets its turns, even if it is not loaded right now
                    // (an absent token is waited for a while when its turn comes, see TokenTurnPartyStep)
                    for (UUID token : tokens) {
                        UUID owner = null;
                        String name = null;
                        if (world.getEntity(token) instanceof TokenizedEntityInterface tokenEntity) {
                            owner = tokenEntity.steveparty$getTokenOwner();
                            Text customName = ((Entity) tokenEntity).getCustomName();
                            if (customName != null) name = customName.getString();
                        }
                        TokenTurnPartyStep turn = new TokenTurnPartyStep(token, owner);
                        turn.setTokenName(name);
                        partyData.addStep(turn);
                    }
                }
                case MINIGAME -> partyData.addStep(new MiniGamePartyStep(new ArrayList<>(tokens)));
                case EVENT -> partyData.addStep(EventPartyStep.eventCard(Math.min(card.count(), 15)));
                case REPEAT -> {
                    // Already expanded
                }
            }
        }
        // Add the end step
        partyData.addStep(new EndPartyStep(new ArrayList<>(tokens)));
    }

    /** At most this many cards once the program is expanded (a party of 10 000 steps is a mistake). */
    public static final int MAX_EXPANDED_CARDS = 2000;

    /**
     * Expands the party program: the cards in reading order, "repeat" cards replaced by the repetitions they ask
     * for. A "repeat" card with N cards in its stack plays the cards since the previous "repeat" card (or the start)
     * N times in all. An empty program is the default party: turns, mini-game, repeated {@code defaultRounds} times.
     */
    public static List<ExpandedCard> expand(List<ItemStack> program, int defaultRounds) {
        List<ExpandedCard> result = new ArrayList<>();
        List<ExpandedCard> group = new ArrayList<>();
        boolean any = program.stream().anyMatch(stack -> stack.getItem() instanceof PartyCardItem);
        if (!any) {
            for (int i = 0; i < Math.max(1, defaultRounds); i++) {
                result.add(new ExpandedCard(PartyCardItem.CardType.TURNS, 1));
                result.add(new ExpandedCard(PartyCardItem.CardType.MINIGAME, 1));
            }
            return result;
        }
        for (ItemStack stack : program) {
            if (!(stack.getItem() instanceof PartyCardItem cardItem)) continue;
            ExpandedCard card = new ExpandedCard(cardItem.getCardType(), stack.getCount());
            if (card.type() == PartyCardItem.CardType.REPEAT) {
                for (int i = 1; i < card.count() && result.size() + group.size() <= MAX_EXPANDED_CARDS; i++)
                    result.addAll(group);
                group.clear();
                continue;
            }
            result.add(card);
            group.add(card);
        }
        if (result.size() > MAX_EXPANDED_CARDS) result.subList(MAX_EXPANDED_CARDS, result.size()).clear();
        return result;
    }

    public record ExpandedCard(PartyCardItem.CardType type, int count) {}
}
