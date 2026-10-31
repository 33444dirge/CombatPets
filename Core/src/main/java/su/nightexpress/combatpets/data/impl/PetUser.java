package su.nightexpress.combatpets.data.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.api.pet.Template;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.combatpets.util.PetUtils;
import su.nightexpress.nightcore.db.AbstractUser;
import su.nightexpress.nightcore.util.TimeUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PetUser extends AbstractUser {

    private final Map<String, PetData> petMap;

    private boolean loaded;
    private long requestedSaveVersion;
    private long persistedSaveVersion;
    private long requestedSaveTime = -1;

    /** Independent of NightCore's unconditional disableAutoSave completion callback. */
    public synchronized void markSaveRequested() {
        this.requestedSaveTime = this.getAutoSaveTime();
        this.requestedSaveVersion++;
    }

    public synchronized long getSaveVersion() {
        return this.requestedSaveVersion;
    }

    /** Only a confirmed JDBC commit may acknowledge the version it actually wrote. */
    public synchronized void acknowledgeSave(long version) {
        this.persistedSaveVersion = Math.max(this.persistedSaveVersion, version);
    }

    @Override
    public synchronized void disableAutoSave() {
        // Serialize NightCore's completion with UserManager.save's deadline + version publication.
        super.disableAutoSave();
    }

    @Override
    public synchronized boolean isAutoSavePlanned() {
        return this.requestedSaveVersion > this.persistedSaveVersion || super.isAutoSavePlanned();
    }

    @Override
    public synchronized boolean isAutoSaveReady() {
        return this.requestedSaveVersion > this.persistedSaveVersion
            ? TimeUtil.isPassed(this.requestedSaveTime) : super.isAutoSaveReady();
    }

    @Override
    public synchronized boolean isCacheExpired() {
        return !this.isAutoSavePlanned() && super.isCacheExpired();
    }

    @NotNull
    public static PetUser create(@NotNull UUID uuid, @NotNull String name) {
        long dateCreated = System.currentTimeMillis();
        long lastOnline = System.currentTimeMillis();
        Map<String, PetData> petMap = new ConcurrentHashMap<>();

        PetUser user = new PetUser(uuid, name, dateCreated, lastOnline, petMap);
        user.load(new HashMap<>());
        return user;
    }

    public PetUser(@NotNull UUID uuid,
                   @NotNull String name,
                   long dateCreated,
                   long lastOnline,
                   @NotNull Map<String, PetData> petMap
    ) {
        super(uuid, name, dateCreated, lastOnline);
        this.petMap = new ConcurrentHashMap<>(petMap);
        this.loaded = true;
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public synchronized void load(@NotNull Map<String, PetData> petMap) {
        this.petMap.clear();
        this.petMap.putAll(petMap);
        this.petMap.values().removeIf(Objects::isNull);
        this.petMap.values().forEach(PetData::refresh);
        this.loaded = true;
    }

    @NotNull
    public Map<String, PetData> getPets() {
        return this.petMap;
    }

    /** Returns detached pet values for asynchronous persistence. */
    @NotNull
    public synchronized Map<String, PetData> getPetsSnapshot() {
        Map<String, PetData> snapshot = new HashMap<>();
        this.petMap.forEach((key, data) -> snapshot.put(key, data.copyForPersistence()));
        return Map.copyOf(snapshot);
    }

    public synchronized void addPet(@NotNull PetData petData) {
        this.petMap.putIfAbsent(PetUtils.getPetKey(petData.getTier(), petData.getConfig()), petData);
        petData.refresh();
    }

    public synchronized void putPet(@NotNull PetData petData) {
        this.petMap.put(PetUtils.getPetKey(petData.getTier(), petData.getConfig()), petData);
    }

    public synchronized boolean removePet(@NotNull Template config, @NotNull Tier tier) {
        return this.petMap.remove(PetUtils.getPetKey(tier, config)) != null;
    }

    public boolean hasPet(@NotNull Template config, @NotNull Tier tier) {
        return this.getPet(config, tier) != null;
    }

    @Nullable
    public PetData getPet(@NotNull Template config, @NotNull Tier tier) {
        return this.getPet(PetUtils.getPetKey(tier, config));
    }

    @NotNull
    public Collection<PetData> getPets(@NotNull Tier tier) {
        Set<PetData> pets = new HashSet<>();
        this.getPets().forEach((key, data) -> {
            if (key.endsWith(tier.getId())) {
                pets.add(data);
                data.tryRevive();
            }
        });
        return pets;
    }

    @Nullable
    public PetData getPet(@NotNull String key) {
        return this.petMap.get(key);
    }
}
