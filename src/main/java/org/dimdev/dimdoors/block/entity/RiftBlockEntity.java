package org.dimdev.dimdoors.block.entity;

import java.util.Arrays;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dimdev.annotatednbt.AnnotatedNbt;
import org.dimdev.annotatednbt.AutoSerializable;
import org.dimdev.annotatednbt.Saved;
import org.dimdev.dimdoors.pockets.PocketTemplate;
import org.dimdev.dimdoors.rift.registry.LinkProperties;
import org.dimdev.dimdoors.rift.registry.Rift;
import org.dimdev.dimdoors.rift.registry.RiftRegistry;
import org.dimdev.dimdoors.rift.targets.EntityTarget;
import org.dimdev.dimdoors.rift.targets.MessageTarget;
import org.dimdev.dimdoors.rift.targets.Target;
import org.dimdev.dimdoors.rift.targets.Targets;
import org.dimdev.dimdoors.rift.targets.VirtualTarget;
import org.dimdev.dimdoors.util.EntityUtils;
import org.dimdev.dimdoors.util.Location;
import org.dimdev.dimdoors.util.NbtUtil;
import org.dimdev.dimdoors.util.RGBA;
import org.dimdev.dimdoors.world.pocket.VirtualLocation;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;

import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;

public abstract class RiftBlockEntity extends BlockEntity implements BlockEntityClientSerializable, Target, EntityTarget, AutoSerializable {
    private static final Logger LOGGER = LogManager.getLogger();

    protected RiftData data = new RiftData();

    protected boolean riftStateChanged; // not saved

    public RiftBlockEntity(BlockEntityType<? extends RiftBlockEntity> type) {
        super(type);
    }

    // NBT
    @Override
    public void fromTag(BlockState state, CompoundTag nbt) {
        super.fromTag(state, nbt);
        if (this.world != null && !this.world.isClient()) {
            this.sync();
        }

        this.data = NbtUtil.deserialize(nbt.get("data"), RiftData.CODEC);
    }


    @Override
    public CompoundTag toTag(CompoundTag tag) {
        if (this.world != null && !this.world.isClient()) {
            this.sync();
        }

        return super.toTag(tag);
    }

    @Override
    public void fromClientTag(CompoundTag tag) {
        tag.put("data", NbtUtil.serialize(data, RiftData.CODEC));
    }

    @Override
    public CompoundTag toClientTag(CompoundTag tag) {
        tag.put("data", NbtUtil.serialize(data, RiftData.CODEC));
        return tag;
    }

    public void setDestination(VirtualTarget destination) {
        if (this.getDestination() != null && isRegistered()) {
            this.getDestination().unregister();
        }
        this.data.setDestination(destination);
        if (destination != null) {
            if (world != null && pos != null) {
                destination.setLocation(new Location((ServerWorld) world, pos));
            }
            if (isRegistered()) destination.register();
        }
        riftStateChanged = true;
        markDirty();
        updateColor();
    }

    public void setColor(RGBA color) {
        data.setColor(color);
        markDirty();
    }

    public void setProperties(LinkProperties properties) {
        data.setProperties(properties);
        updateProperties();
        markDirty();
    }

    public void markStateChanged() {
        riftStateChanged = true;
        markDirty();
    }

    public boolean isRegistered() {
        return !PocketTemplate.isReplacingPlaceholders() && RiftRegistry.instance().isRiftAt(new Location((ServerWorld) world, pos));
    }

    public void register() {
        if (isRegistered()) {
            return;
        }

        Location loc = new Location((ServerWorld) world, pos);
        RiftRegistry.instance().addRift(loc);
        if (data.getDestination() != null) data.getDestination().register();
        updateProperties();
        updateColor();
    }

    public void updateProperties() {
        if (isRegistered())
            RiftRegistry.instance().setProperties(new Location((ServerWorld) world, pos), data.getProperties());
        markDirty();
    }

    public void unregister() {
        if (isRegistered()) {
            RiftRegistry.instance().removeRift(new Location((ServerWorld) world, pos));
        }
    }

    public void updateType() {
        if (!isRegistered()) return;
        Rift rift = RiftRegistry.instance().getRift(new Location((ServerWorld) world, pos));
        rift.isDetached = isDetached();
        rift.markDirty();
    }

    public void handleTargetGone(Location location) {
        if (data.getDestination().shouldInvalidate(location)) {
            data.setDestination(null);
            markDirty();
        }

        updateColor();
    }

    public void handleSourceGone(Location location) {
        updateColor();
    }

    public Target getTarget() {
        if (data.getDestination() == null) {
            return new MessageTarget("rifts.unlinked");
        } else {
            data.getDestination().setLocation(new Location((ServerWorld) world, pos));
            return data.getDestination();
        }
    }

    public boolean teleport(Entity entity) {
        riftStateChanged = false;

        // Attempt a teleport
        try {
            EntityTarget target = getTarget().as(Targets.ENTITY);

            if (target.receiveEntity(entity, entity.yaw)) {
                VirtualLocation vloc = VirtualLocation.fromLocation(new Location((ServerWorld) entity.world, entity.getBlockPos()));
                EntityUtils.chat(entity, new LiteralText("You are at x = " + vloc.x + ", y = ?, z = " + vloc.z + ", w = " + vloc.depth));
                return true;
            }
        } catch (Exception e) {
            EntityUtils.chat(entity, new LiteralText("Something went wrong while trying to teleport you, please report this bug."));
            LOGGER.error("Teleporting failed with the following exception: ", e);
        }

        return false;
    }

    public void updateColor() {
        if (data.isForcedColor()) return;
        if (!isRegistered()) {
            data.setColor(new RGBA(0, 0, 0, 1));
        } else if (data.getDestination() == null) {
            data.setColor(new RGBA(0.7f, 0.7f, 0.7f, 1));
        } else {
            data.getDestination().setLocation(new Location((ServerWorld) world, pos));
            RGBA newColor = data.getDestination().getColor();
            if (data.getColor() == null && newColor != null || !Objects.equals(data.getColor(), newColor)) {
                data.setColor(newColor);
                markDirty();
            }
        }
    }

    protected abstract boolean isDetached();

    public void copyFrom(DetachedRiftBlockEntity rift) {

        data.setDestination(rift.data.getDestination());
        data.setProperties(rift.data.getProperties());
        data.setAlwaysDelete(rift.data.isAlwaysDelete());
        data.setForcedColor(rift.data.isForcedColor());
    }

    public VirtualTarget getDestination() {
        return data.getDestination();
    }

    public LinkProperties getProperties() {
        return data.getProperties();
    }

    public boolean isAlwaysDelete() {
        return data.isAlwaysDelete();
    }

    public boolean isForcedColor() {
        return data.isForcedColor();
    }

    public RGBA getColor() {
        return data.getColor();
    }
}
