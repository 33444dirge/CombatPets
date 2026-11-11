package su.nightexpress.combatpets.config;

import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.configuration.codec.CodecRegistry;
import su.nightexpress.nightcore.configuration.codec.ConfigCodecs;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TierConfigCommentMigrationCheck {

    private TierConfigCommentMigrationCheck() {
    }

    public static void main(String[] args) throws Exception {
        ConfigCodecs.init(new CodecRegistry());
        Path directory = Files.createTempDirectory("combatpets-tier-comments-");
        Path tier = directory.resolve("common.yml");
        try {
            Files.writeString(tier, """
                Name: <white>普通</white>
                Weight: 75.0
                Attributes:
                  # 管理员设置的属性满点最终值；留空时使用宠物配置中的 Per_Aspect。
                目标值按品质配置的全部关联属性点计算，玩家仍需手动分配属性点。
                路径格式：Attributes.Target.<宠物ID>.<属性ID>。
                  Target:
                    iron_golem:
                      max_health: 100.0
                      attack_damage: 37.5
                    warden:
                      max_health: 500.0
                      attack_damage: 40.0
                """);

            if (PluginConfigMigration.repairTierAttributeComments(directory) != 1) {
                throw new AssertionError("broken tier config was not repaired");
            }

            FileConfig repaired = FileConfig.load(tier);
            assertDouble(75D, repaired.getDouble("Weight"), "custom tier weight");
            assertDouble(100D, repaired.getDouble("Attributes.Target.iron_golem.max_health"), "iron golem health");
            assertDouble(37.5D, repaired.getDouble("Attributes.Target.iron_golem.attack_damage"), "iron golem damage");
            assertDouble(500D, repaired.getDouble("Attributes.Target.warden.max_health"), "warden health");
            assertDouble(40D, repaired.getDouble("Attributes.Target.warden.attack_damage"), "warden damage");

            String content = Files.readString(tier);
            for (String line : content.lines().toList()) {
                if (line.equals("目标值按品质配置的全部关联属性点计算，玩家仍需手动分配属性点。") ||
                    line.equals("路径格式：Attributes.Target.<宠物ID>.<属性ID>。")) {
                    throw new AssertionError("bare comment line remains after migration");
                }
            }
            if (PluginConfigMigration.repairTierAttributeComments(directory) != 0) {
                throw new AssertionError("tier comment migration is not idempotent");
            }
        }
        finally {
            Files.deleteIfExists(tier);
            Files.deleteIfExists(directory);
        }
    }

    private static void assertDouble(double expected, double actual, String label) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
