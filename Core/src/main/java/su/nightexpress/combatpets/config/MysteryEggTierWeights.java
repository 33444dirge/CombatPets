package su.nightexpress.combatpets.config;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.nightcore.config.ConfigValue;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MysteryEggTierWeights {

    private MysteryEggTierWeights() {

    }

    @NotNull
    public static Map<String, Double> defaults() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("common", 76.31D);
        weights.put("rare", 15.26D);
        weights.put("unique", 7.63D);
        weights.put("mythic", 0.8D);
        return weights;
    }

    @NotNull
    public static ConfigValue<Map<String, Double>> configValue() {
        return ConfigValue.create("Mystery_Egg.Tier_Chances",
            (config, path) -> {
                defaults().forEach((tierId, chance) -> config.addMissing(path + "." + tierId, chance));

                Map<String, Double> chances = new LinkedHashMap<>();
                config.getSection(path).forEach(tierId -> chances.put(tierId.toLowerCase(Locale.ROOT), config.getDouble(path + "." + tierId)));
                return chances;
            },
            (config, path, chances) -> chances.forEach((tierId, chance) -> config.set(path + "." + tierId, chance)),
            MysteryEggTierWeights::defaults,
            "两种神秘宠物蛋抽取各品质的概率权重。",
            "默认值总和为 100，因此数值就是百分比；修改后会按全部有效权重的总和自动换算概率。",
            "可填写自定义品质 ID；不存在、非有限值或小于等于 0 的权重不会参与抽取。"
        );
    }

    public static boolean isUsable(double weight) {
        return Double.isFinite(weight) && weight > 0D;
    }
}
