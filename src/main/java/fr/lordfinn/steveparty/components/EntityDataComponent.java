package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.List;

public record EntityDataComponent(NbtList attributesData, NbtCompound entityData) {
    public static final Codec<NbtList> NBT_LIST_CODEC = Codec.list(NbtCompound.CODEC)
            .xmap(list -> {
                // Convert List<NbtCompound> to NbtList
                NbtList nbtList = new NbtList();
                nbtList.addAll(list); // Add each NbtCompound to NbtList
                return nbtList;
            }, nbtList -> {
                // Convert NbtList to List<NbtCompound>
                List<NbtCompound> list = new java.util.ArrayList<>();
                for (NbtElement element : nbtList) {
                    if (element instanceof NbtCompound compound) {
                        list.add(compound); // Add only NbtCompound elements
                    }
                }
                return list;
            });
    // Codec for serializing/deserializing the component
    public static final Codec<EntityDataComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            NBT_LIST_CODEC.fieldOf("attributesData").forGetter(EntityDataComponent::attributesData),
            NbtCompound.CODEC.fieldOf("entityData").forGetter(EntityDataComponent::entityData)
    ).apply(builder, EntityDataComponent::new));
}
