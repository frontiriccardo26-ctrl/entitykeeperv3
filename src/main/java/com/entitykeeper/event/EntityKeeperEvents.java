package com.entitykeeper.event;

import com.entitykeeper.EntityKeeperMod;
import com.entitykeeper.data.EntityKeeperRule;
import com.entitykeeper.data.EntityKeeperSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = EntityKeeperMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityKeeperEvents {

    private static final Map<UUID, Integer> tickCounters = new HashMap<>();
    private static final Map<UUID, ChunkPos> forceLoadedChunks = new HashMap<>();

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        ServerLevel level = (ServerLevel) event.getLevel();
        String typeName = getEntityTypeName(entity);
        if (typeName == null) return;

        EntityKeeperSavedData data = EntityKeeperSavedData.getOrCreate(level);

        if (data.isBlocked(typeName)) {
            event.setCanceled(true);
            EntityKeeperMod.LOGGER.debug("EntityKeeper: blocked spawn of {} ({})", entity.getUUID(), typeName);
            return;
        }

        if (data.hasRule(typeName)) {
            tickCounters.putIfAbsent(entity.getUUID(), 0);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            EntityKeeperSavedData data = EntityKeeperSavedData.getOrCreate(level);
            if (data.getAllRules().isEmpty()) continue;

            List<Entity> toRemove = new ArrayList<>();

            for (Entity entity : level.getAllEntities()) {
                String typeName = getEntityTypeName(entity);
                if (typeName == null) continue;
                EntityKeeperRule rule = data.getRule(typeName);
                if (rule == null) continue;

                UUID uuid = entity.getUUID();

                if (rule.isForceLoad()) {
                    ChunkPos currentChunk = new ChunkPos(entity.blockPosition());
                    ChunkPos lastChunk = forceLoadedChunks.get(uuid);
                    if (!currentChunk.equals(lastChunk)) {
                        if (lastChunk != null) level.setChunkForced(lastChunk.x, lastChunk.z, false);
                        level.setChunkForced(currentChunk.x, currentChunk.z, true);
                        forceLoadedChunks.put(uuid, currentChunk);
                    }
                }

                if (rule.getDespawnTicks() >= 0) {
                    int ticks = tickCounters.getOrDefault(uuid, 0) + 1;
                    tickCounters.put(uuid, ticks);
                    if (ticks >= rule.getDespawnTicks()) toRemove.add(entity);
                }
            }

            for (Entity entity : toRemove) {
                cleanupEntity(level, entity);
                entity.discard();
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveWorld(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        UUID uuid = entity.getUUID();
        if (tickCounters.containsKey(uuid) || forceLoadedChunks.containsKey(uuid)) {
            cleanupEntity((ServerLevel) event.getLevel(), entity);
        }
    }

    private static void cleanupEntity(ServerLevel level, Entity entity) {
        UUID uuid = entity.getUUID();
        tickCounters.remove(uuid);
        ChunkPos chunk = forceLoadedChunks.remove(uuid);
        if (chunk != null) level.setChunkForced(chunk.x, chunk.z, false);
    }

    private static String getEntityTypeName(Entity entity) {
        ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return rl != null ? rl.toString() : null;
    }
}
