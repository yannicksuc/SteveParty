package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.Entity;
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
        generateSteps(partyData, world);
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

    private void generateSteps(PartyData partyData, ServerWorld world) {
        List<UUID> tokens = partyData.getTokens(); // Assuming this method retrieves the list of tokens
        if (tokens.isEmpty()) return;

        // Idempotent: drop what a previous run of this generator produced (restart / previous step)
        List<PartyStep> steps = partyData.getSteps();
        int generatorIndex = steps.indexOf(this);
        if (generatorIndex >= 0 && generatorIndex + 1 < steps.size())
            steps.subList(generatorIndex + 1, steps.size()).clear();

        // Add steps for each turn
        for (int i = 0; i < partyData.getNbTurn(); i++) {
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
            // Mini-game step
            partyData.addStep(new MiniGamePartyStep(new ArrayList<>(tokens)));
        }

        // Add the end step
        partyData.addStep(new EndPartyStep(new ArrayList<>(tokens)));

    }
}
