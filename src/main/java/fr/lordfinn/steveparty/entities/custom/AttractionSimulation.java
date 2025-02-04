package fr.lordfinn.steveparty.entities.custom;

import net.minecraft.entity.MovementType;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

public class AttractionSimulation {
    private LivingEntity target; // The entity that does not move
    private final LivingEntity dice;   // The entity that moves
    private Vec3d velocity;      // Current velocity of the moving entity

    public AttractionSimulation(LivingEntity target, LivingEntity dice) {
        this.target = target;
        this.dice = dice;
        this.velocity = Vec3d.ZERO; // Start with zero velocity
    }

    public void tick() {
        if (target == null) {
            return;
        }
        if (dice == null) {
            return;
        }

        // Get positions of entities
        Vec3d targetPosition = target.getPos().add(0, target.getHeight() + dice.getHeight() / 2 + 0.5, 0);
        Vec3d dicePosition = dice.getPos();

        // Calculate displacement
        Vec3d displacement = targetPosition.subtract(dicePosition);

        // Calculate force (spring force - damping)
        // Strength of the attraction
        double springConstant = 0.2;
        // Damping to reduce oscillation
        double dampingFactor = 0.2;
        Vec3d force = displacement.multiply(springConstant).subtract(velocity.multiply(dampingFactor));

        // Update velocity
        // Time step (ticks, adjust as needed)
        double deltaTime = 0.2;
        velocity = velocity.add(force.multiply(deltaTime));

        // Update dice entity position using move() method
        Vec3d moveDelta = velocity.multiply(deltaTime);
        dice.move(MovementType.SELF, moveDelta);
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }
}
