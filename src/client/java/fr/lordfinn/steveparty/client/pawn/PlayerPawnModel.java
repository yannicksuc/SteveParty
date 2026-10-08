package fr.lordfinn.steveparty.client.pawn;

import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/** The statue of a player pawn: the player model posed as a {@link PlayerStatue}, in the pawn's pose. */
public class PlayerPawnModel extends PlayerEntityModel<PlayerPawnEntity> {
    private final ModelPart root;

    public PlayerPawnModel(ModelPart root, boolean thinArms) {
        super(root, thinArms);
        this.root = root;
    }

    @Override
    public void setAngles(PlayerPawnEntity pawn, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        super.setAngles(pawn, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        PlayerStatue.pose(this.root, pawn.getStatuePose());
    }
}
