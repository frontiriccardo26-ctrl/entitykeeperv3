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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EntityKeeperSavedData extends SavedData {

    private static final String DATA_NAME = EntityKeeperMod.MOD_ID;
    private final Map<String, EntityKeeperRule> rules = new HashMap<>();
    private final Set<String> blockedTypes = new HashSet<>();

    public static EntityKeeperSavedData load(CompoundTag tag) {
        EntityKeeperSavedData data = new EntityKeeperSavedData();
        ListTag ruleList = tag.getList("rules", Tag.TAG_COMPOUND);
        for (int i = 0; i < ruleList.size(); i++) {
            CompoundTag entry = ruleList.getCompound(i);
            String type = entry.getString("type");
            boolean forceLoad = entry.getBoolean("forceLoad");
            int despawnTicks = entry.getInt("despawnTicks");
            data.rules.put(type, new EntityKeeperRule(type, forceLoad, despawnTicks));
        }
        ListTag blockedList = tag.getList("blocked", Tag.TAG_COMPOUND);
        for (int i = 0; i < blockedList.size(); i++) {
            data.blockedTypes.add(blockedList.getCompound(i).getString("type"));
        }
        EntityKeeperMod.LOGGER.info("EntityKeeper: loaded {} rule(s), {} blocked type(s).", data.rules.size(), data.blockedTypes.size());
        return data;
    }

    public static EntityKeeperSavedData getOrCreate(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                EntityKeeperSavedData::load,
                EntityKeeperSavedData::new,
                DATA_NAME
        );
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag ruleList = new ListTag();
        for (EntityKeeperRule rule : rules.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("type", rule.getEntityType());
            entry.putBoolean("forceLoad", rule.isForceLoad());
            entry.putInt("despawnTicks", rule.getDespawnTicks());
            ruleList.add(entry);
        }
        tag.put("rules", ruleList);
        ListTag blockedList = new ListTag();
        for (String type : blockedTypes) {
            CompoundTag entry = new CompoundTag();
            entry.putString("type", type);
            blockedList.add(entry);
        }
        tag.put("blocked", blockedList);
        return tag;
    }

    public void setRule(String entityType, boolean forceLoad, int despawnTicks) {
        rules.put(entityType, new EntityKeeperRule(entityType, forceLoad, despawnTicks));
        setDirty();
    }

    public boolean removeRule(String entityType) {
        boolean existed = rules.remove(entityType) != null;
        if (existed) setDirty();
        return existed;
    }

    public EntityKeeperRule getRule(String entityType) { return rules.get(entityType); }
    public boolean hasRule(String entityType)          { return rules.containsKey(entityType); }
    public Collection<EntityKeeperRule> getAllRules()  { return Collections.unmodifiableCollection(rules.values()); }

    public void setBlocked(String entityType, boolean blocked) {
        if (blocked) blockedTypes.add(entityType);
        else         blockedTypes.remove(entityType);
        setDirty();
    }

    public boolean isBlocked(String entityType)  { return blockedTypes.contains(entityType); }
    public Set<String> getAllBlocked()            { return Collections.unmodifiableSet(blockedTypes); }
}
