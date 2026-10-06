package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Several dice thrown at once (Double / Triple Dice): they stop together and add up into one roll. The first die
 * holds the item (given back, spent) and runs the roll; the others follow it and roll the same die (the faces and
 * modules the item carries: see {@code MultiDiceRecipe}).
 */
public abstract class MultiDiceItem extends DefaultDiceItem {
    private final int numberOfDice;

    public MultiDiceItem(Settings settings, int numberOfDice) {
        super(settings);
        this.numberOfDice = numberOfDice;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        // A power-up of the turn still at work (a player being picked, a warp): the roll waits
        if (isServerWorld(world) && fr.lordfinn.steveparty.powerups.PowerUpService.refusesRoll(player))
            return TypedActionResult.fail(player.getStackInHand(hand));
        // A party listing its allowed dice refuses the others
        if (isServerWorld(world) && fr.lordfinn.steveparty.dice.AllowedDice.refusesThrow(player, player.getStackInHand(hand)))
            return TypedActionResult.fail(player.getStackInHand(hand));
        if (isServerWorld(world)) {
            List<DiceEntity> diceEntities = new ArrayList<>();
            for (int i = 0; i < numberOfDice; i++) {
                Vec3d spawnPosition = calculateSpawnPosition(player);
                DiceEntity diceEntity = spawnDiceEntity(world, spawnPosition);
                if (diceEntity != null) {
                    diceEntities.add(diceEntity);
                }
            }

            if (!diceEntities.isEmpty()) {
                linkDiceEntities(diceEntities);
                ItemStack thrown = player.getStackInHand(hand).copyWithCount(1);
                for (int i = 0; i < diceEntities.size(); i++) {
                    DiceEntity dice = diceEntities.get(i);
                    configureDiceEntity(dice, player, hand);
                    playSounds(world, dice);
                    if (i > 0) dice.follow(thrown.copy());
                }
                decrementDiceInHand(player, hand);
                diceEntities.getFirst().startRoll();
            }
        }
        return TypedActionResult.success(player.getStackInHand(hand), world.isClient());
    }

    private void linkDiceEntities(List<DiceEntity> diceEntities) {
        List<UUID> linkedDiceUuids = new ArrayList<>();
        for (DiceEntity diceEntity : diceEntities) {
            linkedDiceUuids.add(diceEntity.getUuid());
        }
        for (DiceEntity diceEntity : diceEntities) {
            diceEntity.setLinkedDice(linkedDiceUuids);
        }
    }
}
