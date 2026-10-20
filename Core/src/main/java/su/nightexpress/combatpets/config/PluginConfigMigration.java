package su.nightexpress.combatpets.config;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.nightcore.config.FileConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class PluginConfigMigration {

    private static final String RANK_PATH = "Pets.Amount_Per_Rank";
    private static final String CHORUS_ITEMS_PATH = "Food.chorus_fruits.Items";
    private static final List<String> BROKEN_ATTRIBUTE_TARGET_COMMENTS = List.of(
        "目标值按品质配置的全部关联属性点计算，玩家仍需手动分配属性点。",
        "路径格式：Attributes.Target.<宠物ID>.<属性ID>。"
    );
    private static final Map<String, Integer> DEFAULT_RANK_LIMITS = createDefaultRankLimits();

    private PluginConfigMigration() {
    }

    public static void migrate(@NotNull PetsPlugin plugin) {
        try {
            int repaired = repairTierAttributeComments(plugin.getDataFolder().toPath().resolve("pets/tiers"));
            if (repaired > 0) plugin.info("已修复 " + repaired + " 个宠物品质配置文件中的属性注释格式。");
        }
        catch (IOException exception) {
            throw new IllegalStateException("无法安全修复宠物品质配置，已停止加载以保护现有数据。", exception);
        }

        FileConfig config = plugin.getConfig();
        boolean changed = migrateRankLimits(config);
        changed |= migrateChorusFruit(config);
        if (!changed) return;

        config.saveChanges();
        // NightPlugin initialized ConfigValue fields before enable(). Refresh
        // them so a migrated server uses the new limits immediately, including
        // after a PlugManX reload, instead of only after the next restart.
        config.initializeOptions(Config.class);
    }

    static int repairTierAttributeComments(@NotNull Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return 0;

        int repaired = 0;
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith(FileConfig.EXTENSION)).toList()) {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                boolean changed = false;

                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index);
                    if (!BROKEN_ATTRIBUTE_TARGET_COMMENTS.contains(line)) continue;

                    lines.set(index, "  # " + line);
                    changed = true;
                }

                if (!changed) continue;

                Files.write(path, lines, StandardCharsets.UTF_8);
                repaired++;
            }
        }
        return repaired;
    }

    static boolean migrateChorusFruit(@NotNull FileConfig config) {
        if (!config.contains("Food.chorus_fruits")) return false;

        String legacyPath = CHORUS_ITEMS_PATH + ".popped_chorus_fruit";
        String chorusPath = CHORUS_ITEMS_PATH + ".chorus_fruit";
        if (config.contains(chorusPath)) return false;

        boolean legacyDefault = config.contains(legacyPath)
            && "POPPED_CHORUS_FRUIT".equalsIgnoreCase(config.getString(legacyPath + ".Item.Material"))
            && Double.compare(config.getDouble(legacyPath + ".Saturation"), 5D) == 0;
        if (legacyDefault) config.remove(legacyPath);

        config.set(chorusPath + ".Item.Material", "CHORUS_FRUIT");
        config.set(chorusPath + ".Saturation", 5D);
        return true;
    }

    static boolean migrateRankLimits(@NotNull FileConfig config) {
        boolean legacyDefaults = config.getInt(RANK_PATH + ".Default_Value") == -1
            && config.getInt(RANK_PATH + ".Values.default") == 10
            && config.getInt(RANK_PATH + ".Values.vip") == 20
            && config.getInt(RANK_PATH + ".Values.admin") == -1
            && !config.contains(RANK_PATH + ".Values.vipp")
            && !config.contains(RANK_PATH + ".Values.mvp")
            && !config.contains(RANK_PATH + ".Values.mvpp");

        boolean changed = false;
        if (legacyDefaults) {
            // Only the admin group should be unlimited in the new defaults.
            config.set(RANK_PATH + ".Default_Value", 10);
            changed = true;
        }

        for (Map.Entry<String, Integer> entry : DEFAULT_RANK_LIMITS.entrySet()) {
            String path = RANK_PATH + ".Values." + entry.getKey();
            if (config.contains(path)) continue;

            config.set(path, entry.getValue());
            changed = true;
        }
        return changed;
    }

    @NotNull
    public static Map<String, Integer> createDefaultRankLimits() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("default", 10);
        limits.put("admin", -1);
        limits.put("vip", 20);
        limits.put("vipp", 30);
        limits.put("mvp", 40);
        limits.put("mvpp", 60);
        return limits;
    }
}
