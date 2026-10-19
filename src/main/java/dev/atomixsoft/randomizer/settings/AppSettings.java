package dev.atomixsoft.randomizer.settings;

import java.util.Objects;

public record AppSettings(Theme theme, boolean excludeFiller, boolean excludeRecaps,
                          GroupWeighting groupWeighting) {

    public enum Theme {
        DARK, LIGHT;

        @Override
        public String toString() {
            return this == DARK ? "Dark" : "Light";
        }
    }

    public enum GroupWeighting {
        PER_EPISODE, PER_ANIME;

        @Override
        public String toString() {
            return this == PER_EPISODE ? "Equal chance per episode" : "Equal chance per anime";
        }
    }

    public AppSettings {
        Objects.requireNonNull(theme, "Appearance is required.");
        Objects.requireNonNull(groupWeighting, "Group weighting is required.");
    }

    public static AppSettings defaults() {
        return new AppSettings(Theme.DARK, false, false, GroupWeighting.PER_EPISODE);
    }
}
