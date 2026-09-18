package me.rexsystems.rexGraves.storage;

import me.rexsystems.rexGraves.grave.Grave;

import java.util.Collection;
import java.util.List;

public interface GraveRepository {

    List<Grave> loadAll();

    void saveAll(Collection<Grave> graves);

    void saveAllSync(Collection<Grave> graves);

    void close();
}
