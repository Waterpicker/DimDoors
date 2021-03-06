package org.dimdev.dimdoors.rift.registry;

import com.mojang.serialization.Codec;
import net.minecraft.util.dynamic.DynamicSerializableUuid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.nbt.CompoundTag;

public class RiftPlaceholder extends Rift { // TODO: don't extend rift
    private static final Logger LOGGER = LogManager.getLogger();

    public static Codec<RiftPlaceholder> CODEC = DynamicSerializableUuid.field_25122.xmap(a -> {
        RiftPlaceholder placeholder = new RiftPlaceholder();
        placeholder.id = a;
        return placeholder;
    }, a -> a.id);

    @Override
    public void sourceGone(RegistryVertex source) {
    }

    @Override
    public void targetGone(RegistryVertex target) {
    }

    @Override
    public void sourceAdded(RegistryVertex source) {
    }

    @Override
    public void targetAdded(RegistryVertex target) {
    }

    @Override
    public void targetChanged(RegistryVertex target) {
    }

    @Override
    public void markDirty() {

    }

    @Override
    public RegistryVertexType<? extends RegistryVertex> getType() {
        return RegistryVertexType.RIFT_PLACEHOLDER;
    }
}
