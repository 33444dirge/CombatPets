package su.nightexpress.combatpets.pet.impl;

public final class PetHealthBarProgressCheck {

    private PetHealthBarProgressCheck() {
    }

    public static void main(String[] args) {
        assertProgress(0D, -1D, 20D, "negative health");
        assertProgress(0D, 0D, 20D, "empty health");
        assertProgress(0.5D, 10D, 20D, "partial health");
        assertProgress(1D, 20D, 20D, "full health");
        assertProgress(1D, 1.0000000419197501D, 1D, "observed rounding overflow");
        assertProgress(0D, 20D, 0D, "zero max health");
        assertProgress(0D, Double.NaN, 20D, "invalid health");
        assertProgress(0D, 20D, Double.POSITIVE_INFINITY, "invalid max health");
    }

    private static void assertProgress(double expected, double health, double maxHealth, String label) {
        double actual = HealthBarProgress.normalize(health, maxHealth);
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
