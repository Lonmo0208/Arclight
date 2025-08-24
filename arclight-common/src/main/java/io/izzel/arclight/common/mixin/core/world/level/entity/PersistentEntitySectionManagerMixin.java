package io.izzel.arclight.common.mixin.core.world.level.entity;

import io.izzel.arclight.common.bridge.core.entity.EntityBridge;
import io.izzel.arclight.mixin.Decorate;
import io.izzel.arclight.mixin.Local;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.bukkit.craftbukkit.v.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin<T extends EntityAccess> {

    // @formatter:off
    @Shadow public abstract void close() throws IOException;
    @Shadow @Final private EntityPersistentStorage<T> permanentStorage;
    @Shadow @Final EntitySectionStorage<T> sectionStorage;
    @Shadow @Final private Long2ObjectMap<PersistentEntitySectionManager.ChunkLoadStatus> chunkLoadStatuses;
    // @formatter:on

    public void close(boolean save) throws IOException {
        if (save) {
            this.close();
        } else {
            this.permanentStorage.close();
        }
    }

    public List<Entity> getEntities(ChunkPos chunkCoordIntPair) {
        List<Entity> snapshot = new ArrayList<>();
        sectionStorage.getExistingSectionsInChunk(chunkCoordIntPair.toLong())
                .forEach(section -> {
                    synchronized (section) {
                        section.getEntities().forEach(entity -> {
                            if (entity != null) {
                                snapshot.add((Entity) entity);
                            }
                        });
                    }
                });
        return snapshot;
    }

    public boolean isPending(long cord) {
        return this.chunkLoadStatuses.get(cord) == PersistentEntitySectionManager.ChunkLoadStatus.PENDING;
    }

    @Unique private final AtomicBoolean arclight$fireEvent = new AtomicBoolean(false);

    @Decorate(method = "storeChunkSections", inject = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityPersistentStorage;storeEntities(Lnet/minecraft/world/level/entity/ChunkEntities;)V"))
    private void arclight$fireUnload(long pos, @Local(ordinal = -1) List<T> list) {
        if (arclight$fireEvent.get()) {
            List<Entity> entities = list.stream()
                    .map(entity -> (Entity) entity)
                    .collect(Collectors.toList());

            CraftEventFactory.callEntitiesUnloadEvent(
                    ((EntityStorage) permanentStorage).level,
                    new ChunkPos(pos),
                    entities
            );
        }
    }

    @Inject(method = "storeChunkSections", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityPersistentStorage;storeEntities(Lnet/minecraft/world/level/entity/ChunkEntities;)V"))
    private void arclight$resetFlag(long pos, Consumer<T> consumer, CallbackInfoReturnable<Boolean> cir) {
        arclight$fireEvent.set(false);
    }

    @Inject(method = "processChunkUnload", at = @At("HEAD"))
    private void arclight$fireEvent(long pChunkPosValue, CallbackInfoReturnable<Boolean> cir) {
        arclight$fireEvent.set(true);
    }

    @Inject(method = "processPendingLoads", locals = LocalCapture.CAPTURE_FAILHARD, at = @At(value = "INVOKE", shift = At.Shift.AFTER, remap = false, target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;put(JLjava/lang/Object;)Ljava/lang/Object;"))
    private void arclight$fireLoad(CallbackInfo ci, ChunkEntities<T> chunkEntities) {
        List<Entity> entities = getEntities(chunkEntities.getPos());
        CraftEventFactory.callEntitiesLoadEvent(
                ((EntityStorage) permanentStorage).level,
                chunkEntities.getPos(),
                entities
        );
    }

    @Inject(method = "unloadEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityAccess;setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"))
    private void arclight$unloadCause(EntityAccess entityAccess, CallbackInfo ci) {
        if (entityAccess instanceof EntityBridge bridge) {
            synchronized (entityAccess) {
                bridge.bridge$pushEntityRemoveCause(EntityRemoveEvent.Cause.UNLOAD);
            }
        }
    }
}