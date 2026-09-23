package me.rexsystems.rexGraves.util;

import org.bukkit.entity.Player;

/**
 * Total experience points helpers (not vanilla death-drop XP).
 * Formulas match Minecraft's level curves.
 */
public final class ExperienceUtils {

    private ExperienceUtils() {
    }

    public static int getExp(Player player) {
        if (player == null) {
            return 0;
        }
        return getExpFromLevel(player.getLevel())
                + Math.round(getExpToNext(player.getLevel()) * player.getExp());
    }

    public static int getExpFromLevel(int level) {
        if (level > 30) {
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
        if (level > 15) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return level * level + 6 * level;
    }

    public static int getExpToNext(int level) {
        if (level >= 30) {
            return level * 9 - 158;
        }
        if (level >= 15) {
            return level * 5 - 38;
        }
        return level * 2 + 7;
    }

    public static double getLevelFromExp(long exp) {
        int level = getIntLevelFromExp(exp);
        // sqrt rounding can land one level off right at a level boundary
        while (level > 0 && getExpFromLevel(level) > exp) {
            level--;
        }
        while (getExpFromLevel(level + 1) <= exp) {
            level++;
        }
        double remainder = exp - (double) getExpFromLevel(level);
        double progress = remainder / getExpToNext(level);
        return ((double) level) + progress;
    }

    public static int getIntLevelFromExp(long exp) {
        if (exp > 1395) {
            return (int) ((Math.sqrt(72 * exp - 54215D) + 325) / 18);
        }
        if (exp > 315) {
            return (int) (Math.sqrt(40 * exp - 7839D) / 10 + 8.1);
        }
        if (exp > 0) {
            return (int) (Math.sqrt(exp + 9D) - 3);
        }
        return 0;
    }

    public static void giveExp(Player player, int amount) {
        if (player == null || amount == 0) {
            return;
        }
        long total = (long) getExp(player) + amount;
        if (total < 0L) {
            total = 0L;
        }
        double levelAndExp = getLevelFromExp(total);
        int level = (int) levelAndExp;
        player.setLevel(level);
        player.setExp((float) (levelAndExp - level));
    }
}
