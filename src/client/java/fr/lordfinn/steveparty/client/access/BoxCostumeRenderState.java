package fr.lordfinn.steveparty.client.access;

import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeAnimatable;
import org.jetbrains.annotations.Nullable;

/** A player's Box Costume on its render state: the animated box (null: none) and whether he is fully inside it. */
public interface BoxCostumeRenderState {
    @Nullable
    BoxCostumeAnimatable steveparty$getBoxCostume();

    boolean steveparty$isInBox();

    void steveparty$setBoxCostume(@Nullable BoxCostumeAnimatable box, boolean inBox);
}
