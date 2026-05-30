package com.entitykeeper.data;

import com.entitykeeper.EntityKeeperMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Saves / loads all EntityKeeper rules to the world's saved-data NBT file.
 * File location: <world>/data/entitykeeper.dat
 */
public class EntityKeeperSavedData extends SavedData {

    private static final String DATA_NAME = EntityKeeperMod.MOD_ID;

    // entity resource-location string → rule
    private final Map<String, EntityKeeperRule> rules = new HashMap<>();

    // ── Factory / load ────────────────────────────────────────────────────────

    public static EntityKeeperSavedData load(CompoundTag tag) {
        EntityKeeperSavedData data = new EntityKeeperSavedData();
        ListTag list = tag.getList("rules", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String  type        = entry.getString("type");
            boolean forceLoad   = entry.getBoolean("forceLoad");
            int     despawnTicks= entry.getInt("despawnTicks");
            data.rules.put(type, new EntityKeeperRule(type, forceLoad, despawnTicks));
        }
        EntityKeeperMod.LOGGER.info("EntityKeeper: loaded {} rule(s) from disk.", data.rules.size());
        return data;
    }

    public static EntityKeeperSavedData getOrCreate(ServerLevel level) {
        // Use the overworld's data storage so rules are global (not per-dimension)
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                EntityKeeperSavedData::load,
                EntityKeeperSavedData::new,
                DATA_NAME
        );
    }

    // ── SavedData interface ───────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (EntityKeeperRule rule : rules.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("type",         rule.getEntityType());
            entry.putBoolean("forceLoad",   rule.isForceLoad());
            entry.putInt("despawnTicks",    rule.getDespawnTicks());
            list.add(entry);
        }
        tag.put("rules", list);
        return tag;
    }

    // ── Rule management ───────────────────────────────────────────────────────

    public void setRule(String entityType, boolean forceLoad, int despawnTicks) {
        rules.put(entityType, new EntityKeeperRule(entityType, forceLoad, despawnTicks));
        setDirty();
    }

    public boolean removeRule(String entityType) {
        boolean existed = rules.remove(entityType) != null;
        if (existed) setDirty();
        return existed;
    }

    public EntityKeeperRule getRule(String entityType) {
        return rules.get(entityType);
    }

    public boolean hasRule(String entityType) {
        return rules.containsKey(entityType);
    }

    public Collection<EntityKeeperRule> getAllRules() {
        return Collections.unmodifiableCollection(rules.values());
    }
}
