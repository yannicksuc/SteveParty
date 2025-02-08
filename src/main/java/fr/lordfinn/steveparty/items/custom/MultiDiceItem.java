package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MultiDiceItem extends DefaultDiceItem {
    private final int numberOfDice;

    public MultiDiceItem(Settings settings, int numberOfDice) {
        super(settings);
        this.numberOfDice = numberOfDice;
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
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
                AtomicInteger i = new AtomicInteger();
                diceEntities.forEach(dice -> {
                    configureDiceEntity(dice, player, hand);
                    playSounds(world, dice);
                    if (i.get() > 0) {
                        dice.setItemReference(ItemStack.EMPTY);
                    } else
                        decrementDiceInHand(player, hand);
                    i.getAndIncrement();
                });
            }
        }
        return ActionResult.SUCCESS;
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
