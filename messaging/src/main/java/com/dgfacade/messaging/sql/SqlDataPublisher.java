/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential. Patent Pending.
 */
package com.dgfacade.messaging.sql;

import com.dgfacade.messaging.core.*;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * SQL-based DataPublisher. Writes messages to a database table.
 * Schedule-based by default. Table: dg_messages(id, topic, payload, status, created_at).
 */
public class SqlDataPublisher extends AbstractPublisher {

    private DataSource dataSource;
    private String tableName = "dg_messages";

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected void doConnect() {
        tableName = (String) config.getOrDefault("table_name", "dg_messages");
        state = ConnectionState.CONNECTED;
        log.info("SQL publisher connected, table: {}", tableName);
    }

    @Override
    protected CompletableFuture<Void> doPublish(String topic, MessageEnvelope envelope) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + tableName + " (id, topic, payload, status, created_at) VALUES (?, ?, ?, 'PENDING', ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, envelope.getMessageId());
                ps.setString(2, topic);
                ps.setString(3, envelope.getPayload());
                ps.setTimestamp(4, Timestamp.from(envelope.getTimestamp()));
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("SQL publish failed", e);
            }
        });
    }

    @Override
    protected void doDisconnect() { state = ConnectionState.DISCONNECTED; }

    @Override
    public boolean isConnected() { return state == ConnectionState.CONNECTED && dataSource != null; }
}
