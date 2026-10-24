package dev.msdrop.database.table;

import dev.msdrop.database.datasource.DataSource;
import dev.msdrop.mSDrop;
import dev.msdrop.model.User;
import dev.msdrop.utils.async.Async;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public class UsersTable {

    private static final String TABLE_NAME = "msdrop_users";

    private final DataSource dataSource;

    public UsersTable(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean createTable() {
        String sql = String.format("CREATE TABLE IF NOT EXISTS `%s` (" +
                "`uniqueId` VARCHAR(36) NOT NULL PRIMARY KEY," +
                "`enabled` BOOLEAN NOT NULL)", TABLE_NAME);

        try (PreparedStatement statement = dataSource.getConnection().prepareStatement(sql)) {
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            log(e);
            return false;
        }
    }

    public CompletableFuture<Void> save(User user) {
        return Async.run(() -> {
            String sql = String.format("INSERT INTO `%s` (`uniqueId`, `enabled`) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE `enabled` = VALUES(`enabled`)", TABLE_NAME);

            try (PreparedStatement statement = dataSource.getConnection().prepareStatement(sql)) {
                statement.setString(1, user.getUniqueId().toString());
                statement.setBoolean(2, user.isEnabled());
                statement.executeUpdate();
            } catch (SQLException e) {
                log(e);
            }
        });
    }

    public CompletableFuture<User> getByUuid(UUID uuid) {
        return Async.supply(() -> {
            String sql = String.format("SELECT `enabled` FROM `%s` WHERE `uniqueId` = ?", TABLE_NAME);

            try (PreparedStatement statement = dataSource.getConnection().prepareStatement(sql)) {
                statement.setString(1, uuid.toString());

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next())
                        return new User(uuid, result.getBoolean("enabled"));
                }
            } catch (SQLException e) {
                log(e);
                throw new CompletionException(e);
            }
            return null;
        });
    }

    private void log(SQLException e) {
        JavaPlugin.getPlugin(mSDrop.class).getLogger().log(Level.SEVERE, "Database error", e);
    }

}
