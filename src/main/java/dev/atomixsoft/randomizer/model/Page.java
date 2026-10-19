package dev.atomixsoft.randomizer.model;

import java.util.List;

public record Page<T>(List<T> items, int number, boolean hasNextPage) {

    public Page {
        items = List.copyOf(items);

        if(number < 1)
            throw new IllegalArgumentException("Page number must be positive!");
    }

}
