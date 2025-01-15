package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
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
        if (partyControllerEntity.getPartyData().getTokens().isEmpty()) {
            partyControllerEntity.nextStep();
            return;
        }
        ServerWorld world = (ServerWorld) partyControllerEntity.getWorld();
        if (world == null) return;
        PartyData partyData = partyControllerEntity.getPartyData();
        generateSteps(partyData);
        MessageUtils.sendToPlayers(partyData.getOwners(world), Text.translatable("message.steveparty.basic_game_start"), MessageUtils.MessageType.CHAT);
    }

    private void generateSteps(PartyData partyData) {
        List<UUID> tokens = partyData.getTokens(); // Assuming this method retrieves the list of tokens
        if (tokens.isEmpty()) return;

        // Add steps for each turn
        for (int i = 0; i < partyData.getNbTurn(); i++) {
            // Token turn steps
            for (UUID token : tokens) {
                partyData.addStep(new TokenTurnPartyStep(token));
            }
            // Mini-game step
            partyData.addStep(new MiniGamePartyStep(tokens));
        }

        // Add the end step
        partyData.addStep(new EndPartyStep(tokens));

    }
}
