package com.entitykeeper.event;

import com.entitykeeper.EntityKeeperMod;
import com.entitykeeper.data.EntityKeeperRule;
import com.entitykeeper.data.EntityKeeperSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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

    /**
     * Tracks how many ticks each managed entity has been alive since it was first seen.
     * Key: entity UUID, Value: ticks alive under EntityKeeper tracking
     */
    private static final Map<UUID, Integer> tickCounters = new HashMap<>();

    /**
     * Tracks which chunk tickets we have added so we can avoid spamming.
     * Key: entity UUID, Value: last known ChunkPos with our ticket.
     */
    private static final Map<UUID, ChunkPos> forceLoadedChunks = new HashMap<>();

    // ── Entity joins world ────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        Entity entity = event.getEntity();
        ServerLevel level = (ServerLevel) event.getLevel();
        String typeName = getEntityTypeName(entity);
        if (typeName == null) return;

        EntityKeeperSavedData data = EntityKeeperSavedData.getOrCreate(level);
        EntityKeeperRule rule = data.getRule(typeName);
        if (rule == null) return;

        UUID uuid = entity.getUUID();

        // Start tick counter for this entity if not already tracked
        tickCounters.putIfAbsent(uuid, 0);

        EntityKeeperMod.LOGGER.debug("EntityKeeper: tracking new entity {} ({})", uuid, typeName);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Process each dimension separately
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

                // ── ForceLoad: keep the chunk this entity is in loaded ──────
                if (rule.isForceLoad()) {
                    ChunkPos currentChunk = new ChunkPos(entity.blockPosition());
                    ChunkPos lastChunk = forceLoadedChunks.get(uuid);

                    if (!currentChunk.equals(lastChunk)) {
                        // Release old ticket if entity moved to a new chunk
                        if (lastChunk != null) {
                            level.setChunkForced(lastChunk.x, lastChunk.z, false);
                        }
                        // Add new forceload ticket
                        level.setChunkForced(currentChunk.x, currentChunk.z, true);
                        forceLoadedChunks.put(uuid, currentChunk);
                    }
                }

                // ── Despawn timer ────────────────────────────────────────────
                if (rule.getDespawnTicks() >= 0) {
                    int ticks = tickCounters.getOrDefault(uuid, 0) + 1;
                    tickCounters.put(uuid, ticks);

                    if (ticks >= rule.getDespawnTicks()) {
                        toRemove.add(entity);
                    }
                }
            }

            // ── Force-despawn entities that exceeded their timer ─────────────
            for (Entity entity : toRemove) {
                UUID uuid = entity.getUUID();
                EntityKeeperMod.LOGGER.debug(
                    "EntityKeeper: despawning {} ({}) after timer expired.",
                    uuid, getEntityTypeName(entity)
                );
                cleanupEntity(level, entity);
                entity.discard(); // removes the entity without dropping items / triggering death
            }
        }
    }

    // ── Entity removed / untracked cleanup ───────────────────────────────────

    @SubscribeEvent
    public static void onEntityLeaveWorld(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        Entity entity = event.getEntity();
        UUID uuid = entity.getUUID();

        if (tickCounters.containsKey(uuid) || forceLoadedChunks.containsKey(uuid)) {
            ServerLevel level = (ServerLevel) event.getLevel();
            cleanupEntity(level, entity);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void cleanupEntity(ServerLevel level, Entity entity) {
        UUID uuid = entity.getUUID();
        tickCounters.remove(uuid);

        ChunkPos chunk = forceLoadedChunks.remove(uuid);
        if (chunk != null) {
            level.setChunkForced(chunk.x, chunk.z, false);
        }
    }

    private static String getEntityTypeName(Entity entity) {
        ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return rl != null ? rl.toString() : null;
    }
}
