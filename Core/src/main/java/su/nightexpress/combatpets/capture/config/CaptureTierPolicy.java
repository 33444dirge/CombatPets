package su.nightexpress.combatpets.capture.config;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.util.Lists;

import java.util.Set;

public final class CaptureTierPolicy {

    private CaptureTierPolicy() {

    }

    @NotNull
    public static Set<String> defaults() {
        return Lists.newSet("common", "rare");
    }

    @NotNull
    public static ConfigValue<Set<String>> configValue() {
        return ConfigValue.create("Settings.Allowed_Tiers",
            defaults(),
            "捕捉绳可以产出的宠物品质 ID。",
            "默认只允许普通和稀有品质；品质自身的 Catching.Enabled 也必须为 true。"
        );
    }

    public static boolean isAllowed(@NotNull Set<String> allowedTiers, @NotNull String tierId) {
        return allowedTiers.stream().map(String::trim).anyMatch(tierId::equalsIgnoreCase);
    }
}
