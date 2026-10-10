package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.MiniGameGains;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepFactory;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.dice.AllowedDice;
import fr.lordfinn.steveparty.minigame.MiniGameReturns;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * The save format of the Party Controller: a controller saved with every field set (settings, program, star, tokens
 * to release or to send home, start tiles, winners, a party on a mini-game step) loads back and saves exactly the
 * same keys and values, so that the worlds saved before keep loading.
 */
public class PartyControllerSaveGameTests implements SteveGameTest {

    private static UUID uuid(int n) {
        return new UUID(0x5EEDL, n);
    }

    private static NbtList uuids(UUID... uuids) {
        NbtList list = new NbtList();
        for (UUID uuid : uuids) list.add(NbtString.of(uuid.toString()));
        return list;
    }

    /** A save as the controller writes it, every optional key present. */
    private static NbtCompound fullSave(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        NbtCompound players = new NbtCompound();
        players.putString("player_0", uuid(1).toString());
        nbt.put("interestedPlayers", players);
        nbt.putBoolean("isCatalogued", false);

        // The party: a start roll, a mini-game being played (every saved field of its step), the end
        UUID tokenA = uuid(10), tokenB = uuid(11);
        NbtList steps = new NbtList();
        NbtCompound rolls = new NbtCompound();
        rolls.putString("Type", PartyStepType.START_ROLLS.name());
        rolls.putString("Status", PartyStep.Status.FINISHED.name());
        steps.add(rolls);
        NbtCompound miniGame = new NbtCompound();
        miniGame.putString("Type", PartyStepType.MINI_GAME.name());
        miniGame.putString("Status", PartyStep.Status.IN_PROGRESS.name());
        miniGame.put("Tokens", uuids(tokenA, tokenB));
        miniGame.putBoolean("MiniGameChosen", true);
        miniGame.putString("Phase", MiniGamePartyStep.Phase.PRACTICE.name());
        miniGame.put("Participants", uuids(uuid(1), uuid(2)));
        miniGame.put("Ready", uuids(uuid(2)));
        miniGame.put("Winners", uuids(uuid(1)));
        NbtCompound places = new NbtCompound();
        places.putInt(uuid(1).toString(), 1);
        places.putInt(uuid(2).toString(), 0);
        miniGame.put("Places", places);
        NbtCompound returns = new NbtCompound();
        returns.put(uuid(1).toString(), new MiniGameReturns.Return(World.OVERWORLD, new Vec3d(1.5, 64, -2.5), 90f, 10f).toNbt());
        miniGame.put("ReturnPositions", returns);
        miniGame.putInt("ChosenPage", 2);
        steps.add(miniGame);
        NbtCompound end = new NbtCompound();
        end.putString("Type", PartyStepType.END.name());
        end.putString("Status", PartyStep.Status.WAITING.name());
        steps.add(end);
        NbtCompound unknown = new NbtCompound();
        unknown.putString("Type", "NOT_A_STEP");
        unknown.putString("Status", PartyStep.Status.WAITING.name());
        steps.add(unknown);
        nbt.put("Steps", steps);
        nbt.put("Tokens", uuids(tokenA, tokenB));
        nbt.putInt("StepIndex", 1);
        nbt.putInt("NbTurn", 7);

        nbt.put(PartyCurrency.STAR.nbtKey(), new ItemStack(Items.DIAMOND).encode(registries));
        nbt.put(PartyCurrency.COIN.nbtKey(), new ItemStack(Items.GOLD_NUGGET).encode(registries));
        nbt.put("MiniGameGains", MiniGameGains.DEFAULT.toNbt());
        // The chests linked to the controller (kept inside it as an Inventory Cartridge)
        ItemStack bank = new ItemStack(fr.lordfinn.steveparty.items.ModItems.INVENTORY_CARTRIDGE);
        fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers.set(bank,
                java.util.List.of(net.minecraft.util.math.GlobalPos.create(World.OVERWORLD, new BlockPos(7, 64, 7))));
        nbt.put("BankCartridge", bank.encode(registries));
        nbt.putBoolean("BankInternal", true);
        nbt.putBoolean("PracticeRound", false);
        nbt.putInt("MaxPowerUps", 2);
        nbt.putBoolean("RestrictDice", true);
        NbtList dice = new NbtList();
        AllowedDice.defaults().forEach(die -> dice.add(die.encode(registries)));
        nbt.put("AllowedDice", dice);
        nbt.putLong("StarSpace", new BlockPos(4, 5, 6).asLong());
        nbt.put("TokensToRelease", uuids(uuid(20)));
        NbtCompound startTiles = new NbtCompound();
        startTiles.putLong(tokenA.toString(), new BlockPos(1, 2, 3).asLong());
        startTiles.putLong(tokenB.toString(), new BlockPos(-1, 2, 3).asLong());
        nbt.put("StartTiles", startTiles);
        NbtCompound sendHome = new NbtCompound();
        sendHome.putLong(uuid(21).toString(), new BlockPos(9, 9, 9).asLong());
        nbt.put("TokensToSendHome", sendHome);
        nbt.put("LastWinners", uuids(uuid(1)));
        DefaultedList<ItemStack> program = DefaultedList.ofSize(PartyControllerEntity.PROGRAM_SLOTS, ItemStack.EMPTY);
        program.set(0, new ItemStack(Items.PAPER));
        program.set(5, new ItemStack(Items.BOOK, 2));
        NbtCompound programNbt = new NbtCompound();
        Inventories.writeNbt(programNbt, program, registries);
        nbt.put("PartyProgram", programNbt);
        return nbt;
    }

    private static NbtCompound reload(TestContext context, NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        PartyControllerEntity controller = new PartyControllerEntity(context.getAbsolutePos(BlockPos.ORIGIN),
                ModBlocks.PARTY_CONTROLLER.getDefaultState());
        controller.read(nbt, registries);
        return controller.createNbt(registries);
    }

    /** Every key of a full save is read back and written again unchanged (steps included), and a second load changes nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aFullSaveLoadsAndSavesTheSame(TestContext context) {
        RegistryWrapper.WrapperLookup registries = context.getWorld().getRegistryManager();
        NbtCompound saved = fullSave(registries);
        NbtCompound once = reload(context, saved, registries);
        for (String key : saved.getKeys()) {
            if (key.equals("Steps")) continue;
            context.assertTrue(saved.get(key).equals(once.get(key)), "key " + key + " saved again unchanged: " + saved.get(key) + " / " + once.get(key));
        }
        NbtList stepsIn = saved.getList("Steps", 10), stepsOut = once.getList("Steps", 10);
        context.assertEquals(stepsOut.size(), stepsIn.size(), "every step kept (an unknown one as a placeholder)");
        for (int i = 0; i < 3; i++) {
            NbtCompound in = stepsIn.getCompound(i), out = stepsOut.getCompound(i);
            for (String key : in.getKeys())
                context.assertTrue(in.get(key).equals(out.get(key)), "step " + i + " key " + key + " kept: " + in.get(key) + " / " + out.get(key));
        }
        NbtCompound twice = reload(context, once, registries);
        context.assertTrue(once.equals(twice), "a second load saves exactly the same");
        context.complete();
    }

    /** Each step type is rebuilt as its own class from its saved type; an unknown type is a placeholder step. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stepsAreRebuiltByType(TestContext context) {
        for (PartyStepType type : PartyStepType.values()) {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("Type", type.name());
            PartyStep step = PartyStepFactory.get(nbt);
            context.assertTrue(step != null && step.getType() == type, type + " rebuilt with its type");
            if (type == PartyStepType.MINI_GAME) context.assertTrue(step instanceof MiniGamePartyStep, "a mini-game step");
            if (type == PartyStepType.DEFAULT) context.assertTrue(step.getClass() == PartyStep.class, "a plain step");
        }
        NbtCompound unknown = new NbtCompound();
        unknown.putString("Type", "NOT_A_STEP");
        context.assertTrue(PartyStepFactory.get(unknown).getClass() == PartyStep.class, "an unknown type: a placeholder");
        List<PartyStepType> types = List.of(PartyStepType.values());
        context.assertEquals(types.size(), 7, "the seven step types");
        context.complete();
    }
}
