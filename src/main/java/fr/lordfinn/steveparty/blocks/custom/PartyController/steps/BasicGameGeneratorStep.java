package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

public class BasicGameGeneratorStep extends PartyStep {
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
        if (partyControllerEntity.getPartyData().getTokens().isEmpty()) {
            partyControllerEntity.nextStep();
            return;
        }
        ServerWorld world = (ServerWorld) partyControllerEntity.getWorld();
        if (world == null) return;
        PartyData partyData = partyControllerEntity.getPartyData();
        generateSteps(partyData, world);
        Steveparty.SCHEDULER.schedule(UUID.randomUUID(), 20,
            () -> {
                MessageUtils.sendToPlayers(partyData.getOwners(world), Text.translatable("message.steveparty.basic_game_start"), MessageUtils.MessageType.CHAT);
                partyControllerEntity.nextStep();
            }
        );
    }

    private void generateSteps(PartyData partyData, ServerWorld world) {
        List<UUID> tokens = partyData.getTokens(); // Assuming this method retrieves the list of tokens
        if (tokens.isEmpty()) return;

        // Add steps for each turn
        for (int i = 0; i < partyData.getNbTurn(); i++) {
            // Token turn steps
            for (UUID token : tokens) {
                if (world.getEntity(token) instanceof TokenizedEntityInterface  tokenEntity) {
                    UUID owner = tokenEntity.steveparty$getTokenOwner();
                    partyData.addStep(new TokenTurnPartyStep(token, owner));
                }
            }
            // Mini-game step
            partyData.addStep(new MiniGamePartyStep(tokens));
        }

        // Add the end step
        partyData.addStep(new EndPartyStep(tokens));

    }
}
