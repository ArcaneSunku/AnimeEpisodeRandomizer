package dev.atomixsoft.randomizer.persistence;

import dev.atomixsoft.randomizer.model.SelectionRecord;
import java.io.IOException;
import java.util.List;

public interface SelectionHistoryRepository {

    List<SelectionRecord> load() throws IOException;
    void append(SelectionRecord selection) throws IOException;

}
