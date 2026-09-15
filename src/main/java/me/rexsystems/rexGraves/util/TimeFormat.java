package me.rexsystems.rexGraves.util;

public final class TimeFormat {

    private TimeFormat() {
    }

    public static String formatRemaining(long remainingMillis, String neverLabel, String expiredLabel, String pattern) {
        if (remainingMillis < 0) {
            return neverLabel == null ? "Never expires" : neverLabel;
        }
        if (remainingMillis == 0) {
            return expiredLabel == null ? "Expired" : expiredLabel;
        }
        return formatDuration(remainingMillis, pattern);
    }

    public static String formatElapsed(long elapsedMillis, String pattern) {
        return formatDuration(Math.max(0L, elapsedMillis), pattern);
    }

    public static String formatDuration(long millis, String pattern) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        String format = pattern == null || pattern.isBlank()
                ? "{hours}h {minutes}m {seconds}s"
                : pattern;

        return format
                .replace("{hours}", String.valueOf(hours))
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{seconds}", String.valueOf(seconds));
    }
}
