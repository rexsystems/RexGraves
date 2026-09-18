package me.rexsystems.rexGraves.storage;

import me.rexsystems.rexGraves.RexGraves;

import java.io.File;

public final class SqliteGraveRepository extends SqlGraveRepository {

    public SqliteGraveRepository(RexGraves plugin) {
        super(plugin, createSqlitePool(plugin, resolveFile(plugin)));
    }

    private static File resolveFile(RexGraves plugin) {
        String name = plugin.getConfigManager().sqliteFile();
        if (name == null || name.isBlank()) {
            name = "graves.db";
        }
        File file = new File(name);
        if (!file.isAbsolute()) {
            file = new File(plugin.getDataFolder(), name);
        }
        return file;
    }

    @Override
    protected String upsertSql() {
        return """
                INSERT INTO %s (
                    id, owner_id, owner_name, world, x, y, z, yaw, pitch,
                    experience, created_at, expires_at, items, marker_uuid, clickbox_uuid, hologram_uuids
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    owner_id=excluded.owner_id,
                    owner_name=excluded.owner_name,
                    world=excluded.world,
                    x=excluded.x,
                    y=excluded.y,
                    z=excluded.z,
                    yaw=excluded.yaw,
                    pitch=excluded.pitch,
                    experience=excluded.experience,
                    created_at=excluded.created_at,
                    expires_at=excluded.expires_at,
                    items=excluded.items,
                    marker_uuid=excluded.marker_uuid,
                    clickbox_uuid=excluded.clickbox_uuid,
                    hologram_uuids=excluded.hologram_uuids
                """.formatted(TABLE);
    }
}
