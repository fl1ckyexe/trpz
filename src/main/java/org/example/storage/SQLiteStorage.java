package org.example.storage;

import org.example.model.DownloadSegment;
import org.example.model.DownloadStatus;
import org.example.model.DownloadTask;
import org.example.model.SegmentStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class SQLiteStorage implements LocalStorage {

    private final String dbUrl;

    public SQLiteStorage(String filePath) {
        // Example: "jdbc:sqlite:downloadmanager.db"
        this.dbUrl = "jdbc:sqlite:" + filePath;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    @Override
    public void init() {
        try (Connection c = connect();
             Statement st = c.createStatement()) {

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS download_tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    url TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    status TEXT NOT NULL,
                    total_bytes INTEGER,
                    downloaded_bytes INTEGER
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS download_segments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id INTEGER NOT NULL,
                    seg_index INTEGER NOT NULL,
                    start_byte INTEGER NOT NULL,
                    end_byte INTEGER NOT NULL,
                    downloaded_bytes INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    FOREIGN KEY(task_id) REFERENCES download_tasks(id)
                )
            """);

        } catch (SQLException e) {
            throw new RuntimeException("SQLite init failed", e);
        }
    }

    @Override
    public DownloadTask createTask(String url, String fileName) {
        String sql = """
            INSERT INTO download_tasks
            (url, file_name, status, total_bytes, downloaded_bytes)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, url);
            ps.setString(2, fileName);
            ps.setString(3, DownloadStatus.CREATED.name());
            ps.setLong(4, -1);
            ps.setLong(5, 0);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    return new DownloadTask(id, url, fileName);
                }
            }

            throw new RuntimeException("Cannot create download task");

        } catch (SQLException e) {
            throw new RuntimeException("SQLite createTask failed", e);
        }
    }

    @Override
    public Optional<DownloadTask> findTask(long taskId) {
        String sql = """
            SELECT id, url, file_name, status, total_bytes, downloaded_bytes
            FROM download_tasks WHERE id = ?
        """;

        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, taskId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                DownloadTask task = new DownloadTask(
                        rs.getLong("id"),
                        rs.getString("url"),
                        rs.getString("file_name")
                );

                task.setStatus(DownloadStatus.valueOf(rs.getString("status")));
                task.setTotalBytes(rs.getLong("total_bytes"));
                task.setDownloadedBytes(rs.getLong("downloaded_bytes"));

                return Optional.of(task);
            }

        } catch (SQLException e) {
            throw new RuntimeException("SQLite findTask failed", e);
        }
    }

    @Override
    public void updateTask(DownloadTask task) {
        String sql = """
            UPDATE download_tasks
            SET status = ?, total_bytes = ?, downloaded_bytes = ?
            WHERE id = ?
        """;

        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, task.getStatus().name());
            ps.setLong(2, task.getTotalBytes());
            ps.setLong(3, task.getDownloadedBytes());
            ps.setLong(4, task.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("SQLite updateTask failed", e);
        }
    }

    @Override
    public void saveSegments(long taskId, List<DownloadSegment> segments) {
        try (Connection c = connect()) {
            c.setAutoCommit(false);

            try (PreparedStatement del =
                         c.prepareStatement("DELETE FROM download_segments WHERE task_id = ?")) {
                del.setLong(1, taskId);
                del.executeUpdate();
            }

            String insertSql = """
                INSERT INTO download_segments
                (task_id, seg_index, start_byte, end_byte, downloaded_bytes, status)
                VALUES (?, ?, ?, ?, ?, ?)
            """;

            try (PreparedStatement ps = c.prepareStatement(insertSql)) {
                for (DownloadSegment s : segments) {
                    ps.setLong(1, taskId);
                    ps.setInt(2, s.getIndex());
                    ps.setLong(3, s.getStartByte());
                    ps.setLong(4, s.getEndByte());
                    ps.setLong(5, s.getDownloadedBytes());
                    ps.setString(6, s.getStatus().name());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            c.commit();

        } catch (SQLException e) {
            throw new RuntimeException("SQLite saveSegments failed", e);
        }
    }

    @Override
    public List<DownloadSegment> loadSegments(long taskId) {
        String sql = """
            SELECT id, seg_index, start_byte, end_byte, downloaded_bytes, status
            FROM download_segments
            WHERE task_id = ?
            ORDER BY seg_index
        """;

        List<DownloadSegment> result = new ArrayList<>();

        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, taskId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DownloadSegment segment = new DownloadSegment(
                            rs.getLong("id"),
                            taskId,
                            rs.getInt("seg_index"),
                            rs.getLong("start_byte"),
                            rs.getLong("end_byte")
                    );
                    segment.setDownloadedBytes(rs.getLong("downloaded_bytes"));
                    segment.setStatus(SegmentStatus.valueOf(rs.getString("status")));
                    result.add(segment);
                }
            }

            return result;

        } catch (SQLException e) {
            throw new RuntimeException("SQLite loadSegments failed", e);
        }
    }

    @Override
    public void updateSegment(DownloadSegment segment) {
        String sql = """
            UPDATE download_segments
            SET downloaded_bytes = ?, status = ?
            WHERE id = ?
        """;

        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, segment.getDownloadedBytes());
            ps.setString(2, segment.getStatus().name());
            ps.setLong(3, segment.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("SQLite updateSegment failed", e);
        }
    }
}
