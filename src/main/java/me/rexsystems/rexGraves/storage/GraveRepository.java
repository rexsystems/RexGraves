package me.rexsystems.rexGraves.storage;

import me.rexsystems.rexGraves.grave.Grave;

import java.util.Collection;
import java.util.List;

public interface GraveRepository {

    List<Grave> loadAll();

    /**
     * Snapshot now, write async.
     * @param onFailure run (on the writer thread) if the write fails, so the caller can retry later
     */
    void saveAll(Collection<Grave> graves, Runnable onFailure);

    void saveAllSync(Collection<Grave> graves);

    void close();
}
