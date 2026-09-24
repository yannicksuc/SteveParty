package fr.lordfinn.steveparty.blocks.custom.signs;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.StencilCanvasComponent;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Block entity of every block a stencil can be applied to (traffic signs, panels, rock signs, plastic road signs,
 * sprayed paint). It holds the symbol and what the block itself is made of:
 * <ul>
 *     <li>{@code shape}: the stencil applied, null when nothing was applied;</li>
 *     <li>{@code color}: its paint, null when it is only engraved;</li>
 *     <li>{@code glowing}: glow ink applied;</li>
 *     <li>{@code material}: planks / rock block of a material sign (null for the fixed-wood legacy signs);</li>
 *     <li>{@code plateColor}: colour of a plastic road sign.</li>
 * </ul>
 * All of it is kept by the item when the block is broken (see {@link #addComponents}).
 */
public class StencilCanvasBlockEntity extends BlockEntity implements RenderDataBlockEntity {
    private static final String SHAPE_KEY = "SymbolShape";
    private static final String COLOR_KEY = "Color";
    private static final String ENGRAVED_KEY = "Engraved";
    private static final String GLOWING_KEY = "IsGlowing";
    private static final String MATERIAL_KEY = "Material";
    private static final String PLATE_COLOR_KEY = "PlateColor";

    private @Nullable byte[] shape = null;
    private @Nullable DyeColor color = DyeColor.WHITE;
    private boolean glowing = false;
    private @Nullable Identifier material = null;
    private @Nullable DyeColor plateColor = null;

    public StencilCanvasBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.STENCIL_CANVAS, pos, state);
    }

    protected StencilCanvasBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---------------------------------------------------------------- symbol

    /** @return a copy of the applied shape, or null. */
    public @Nullable byte[] getShape() {
        return shape == null ? null : shape.clone();
    }

    public boolean hasShape() {
        return shape != null;
    }

    public @Nullable DyeColor getColor() {
        return color;
    }

    public boolean isEngraved() {
        return color == null;
    }

    public boolean isGlowing() {
        return glowing;
    }

    /** Applies a stencil: its shape, painted with {@code color} (null: engraved only). */
    public void setSymbol(@Nullable byte[] shape, @Nullable DyeColor color) {
        this.shape = shape == null ? null : StencilShape.sanitize(shape);
        this.color = color;
        onChanged();
    }

    public void setColor(@Nullable DyeColor color) {
        this.color = color;
        onChanged();
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
        onChanged();
    }

    /** Washes the symbol off: no shape, no paint, no glow. */
    public void clearSymbol() {
        this.shape = null;
        this.color = DyeColor.WHITE;
        this.glowing = false;
        onChanged();
    }

    /** Legacy name, kept for the traffic sign code. */
    public void setShape(@Nullable byte[] shape) {
        setSymbol(shape, color);
    }

    // ---------------------------------------------------------------- what the block is made of

    public @Nullable Identifier getMaterial() {
        return material;
    }

    public void setMaterial(@Nullable Identifier material) {
        this.material = material;
        onChanged();
    }

    public @Nullable DyeColor getPlateColor() {
        return plateColor;
    }

    public void setPlateColor(@Nullable DyeColor plateColor) {
        this.plateColor = plateColor;
        onChanged();
    }

    // ---------------------------------------------------------------- sync

    private void onChanged() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    /** What the chunk mesh needs to draw the block itself (the symbol is drawn by the block entity renderer). */
    public record RenderData(@Nullable Identifier material, @Nullable DyeColor plateColor, @Nullable byte[] shape) {
    }

    @Override
    public Object getRenderData() {
        return new RenderData(material, plateColor, shape);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (shape != null) nbt.putByteArray(SHAPE_KEY, shape);
        if (color != null) nbt.putString(COLOR_KEY, color.getName());
        else nbt.putBoolean(ENGRAVED_KEY, true);
        nbt.putBoolean(GLOWING_KEY, glowing);
        if (material != null) nbt.putString(MATERIAL_KEY, material.toString());
        if (plateColor != null) nbt.putString(PLATE_COLOR_KEY, plateColor.getName());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        byte[] oldShape = shape;
        Identifier oldMaterial = material;
        DyeColor oldPlate = plateColor;

        shape = nbt.contains(SHAPE_KEY, NbtElement.BYTE_ARRAY_TYPE) && StencilShape.isValid(nbt.getByteArray(SHAPE_KEY))
                ? StencilShape.sanitize(nbt.getByteArray(SHAPE_KEY)) : null;
        // Signs saved before engraving existed have no Engraved flag and default to white paint
        if (nbt.getBoolean(ENGRAVED_KEY)) color = null;
        else color = DyeColor.byName(nbt.getString(COLOR_KEY), DyeColor.WHITE);
        glowing = nbt.getBoolean(GLOWING_KEY);
        material = nbt.contains(MATERIAL_KEY, NbtElement.STRING_TYPE) ? Identifier.tryParse(nbt.getString(MATERIAL_KEY)) : null;
        plateColor = nbt.contains(PLATE_COLOR_KEY, NbtElement.STRING_TYPE) ? DyeColor.byName(nbt.getString(PLATE_COLOR_KEY), null) : null;

        // Client: what the chunk mesh draws changed, rebuild it
        if (world != null && world.isClient && (!Objects.equals(oldMaterial, material) || oldPlate != plateColor
                || !java.util.Arrays.equals(oldShape, shape))) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        }
    }

    @Override
    protected void readComponents(ComponentsAccess components) {
        super.readComponents(components);
        Identifier materialId = components.get(ModComponents.SIGN_MATERIAL);
        if (materialId != null) material = materialId;
        DyeColor base = components.get(DataComponentTypes.BASE_COLOR);
        if (base != null) plateColor = base;
        StencilCanvasComponent canvas = components.get(ModComponents.STENCIL_CANVAS);
        if (canvas != null) {
            shape = canvas.shapeArray();
            color = canvas.color().orElse(null);
            glowing = canvas.glowing();
        }
    }

    @Override
    protected void addComponents(ComponentMap.Builder builder) {
        super.addComponents(builder);
        if (material != null) builder.add(ModComponents.SIGN_MATERIAL, material);
        if (plateColor != null) builder.add(DataComponentTypes.BASE_COLOR, plateColor);
        if (shape != null || glowing) {
            builder.add(ModComponents.STENCIL_CANVAS, StencilCanvasComponent.of(shape == null ? StencilShape.blank() : shape, color, glowing));
        }
    }

    @Override
    public void removeFromCopiedStackNbt(NbtCompound nbt) {
        super.removeFromCopiedStackNbt(nbt);
        nbt.remove(SHAPE_KEY);
        nbt.remove(COLOR_KEY);
        nbt.remove(ENGRAVED_KEY);
        nbt.remove(GLOWING_KEY);
        nbt.remove(MATERIAL_KEY);
        nbt.remove(PLATE_COLOR_KEY);
    }
}
