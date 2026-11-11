package su.nightexpress.combatpets.config;

import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.configuration.codec.CodecRegistry;
import su.nightexpress.nightcore.configuration.codec.ConfigCodecs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class MysteryEggTierWeightsCheck {

    private MysteryEggTierWeightsCheck() {

    }

    public static void main(String[] args) throws Exception {
        ConfigCodecs.init(new CodecRegistry());

        Map<String, Double> weights = MysteryEggTierWeights.defaults();
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();

        require(weights.size() == 4, "expected four default tiers");
        require(Double.compare(total, 100D) == 0, "default weights must total 100");
        require(Double.compare(weights.get("mythic"), 0.8D) == 0, "mythic chance must be 0.8%");
        require(weights.values().stream().allMatch(MysteryEggTierWeights::isUsable), "default weights must be usable");
        require(!MysteryEggTierWeights.isUsable(0D), "zero must disable a tier");
        require(!MysteryEggTierWeights.isUsable(-1D), "negative weights must be rejected");
        require(!MysteryEggTierWeights.isUsable(Double.NaN), "NaN must be rejected");
        require(!MysteryEggTierWeights.isUsable(Double.POSITIVE_INFINITY), "infinite weights must be rejected");

        checkOldConfigUpgrade();

        System.out.println("MysteryEggTierWeightsCheck PASS");
    }

    private static void checkOldConfigUpgrade() throws Exception {
        Path oldConfig = Files.createTempFile("combatpets-old-mystery-chances-", ".yml");
        try {
            Files.writeString(oldConfig, "Unrelated: keep\nMystery_Egg:\n  Tier_Chances:\n    rare: 20.0\n");

            FileConfig config = FileConfig.load(oldConfig);
            Map<String, Double> upgraded = MysteryEggTierWeights.configValue().read(config);
            config.saveChanges();

            require(Double.compare(upgraded.get("common"), 76.31D) == 0, "missing common chance was not added");
            require(Double.compare(upgraded.get("rare"), 20D) == 0, "custom rare chance was overwritten");
            require(Double.compare(upgraded.get("unique"), 7.63D) == 0, "missing unique chance was not added");
            require(Double.compare(upgraded.get("mythic"), 0.8D) == 0, "missing mythic chance was not added");

            FileConfig saved = FileConfig.load(oldConfig);
            require("keep".equals(saved.getString("Unrelated")), "unrelated config value was changed");
            require(!saved.getComments("Mystery_Egg.Tier_Chances").isEmpty(), "config comments were not added");
        }
        finally {
            Files.deleteIfExists(oldConfig);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
