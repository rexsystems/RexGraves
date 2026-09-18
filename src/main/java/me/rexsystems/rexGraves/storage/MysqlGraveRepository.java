package me.rexsystems.rexGraves.storage;

import me.rexsystems.rexGraves.RexGraves;

public final class MysqlGraveRepository extends SqlGraveRepository {

    public MysqlGraveRepository(RexGraves plugin) {
        super(plugin, createMysqlPool(plugin));
    }

    @Override
    protected String upsertSql() {
        return """
                INSERT INTO %s (
                    id, owner_id, owner_name, world, x, y, z, yaw, pitch,
                    experience, created_at, expires_at, items, marker_uuid, clickbox_uuid, hologram_uuids
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    owner_id=VALUES(owner_id),
                    owner_name=VALUES(owner_name),
                    world=VALUES(world),
                    x=VALUES(x),
                    y=VALUES(y),
                    z=VALUES(z),
                    yaw=VALUES(yaw),
                    pitch=VALUES(pitch),
                    experience=VALUES(experience),
                    created_at=VALUES(created_at),
                    expires_at=VALUES(expires_at),
                    items=VALUES(items),
                    marker_uuid=VALUES(marker_uuid),
                    clickbox_uuid=VALUES(clickbox_uuid),
                    hologram_uuids=VALUES(hologram_uuids)
                """.formatted(TABLE);
    }
}
