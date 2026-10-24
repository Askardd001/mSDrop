package dev.msdrop.database.datasource.impl;

import dev.msdrop.data.DataManager;
import dev.msdrop.database.datasource.DataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class MySQL implements DataSource {

    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9._-]{0,251}[A-Za-z0-9])?|\\[[0-9A-Fa-f:.]{2,45}]");
    private static final Pattern DATABASE = Pattern.compile("[A-Za-z0-9_$-]{1,64}");

    private final String url;
    private final String user;
    private final String password;

    private Connection connection;

    public MySQL() {
        ConfigurationSection section = DataManager.getInstance().getConfig().getConfigurationSection("database-settings");

        if (section == null)
            throw new IllegalStateException("database-settings is missing from config.yml");

        String host = section.getString("host", "").trim();
        int port = section.getInt("port", 3306);
        String database = section.getString("database", "").trim();

        // The values go straight into the JDBC url, so anything that could add or change a
        // connection property (?, &, =, /, @, spaces...) is refused instead of escaped.
        if (!HOST.matcher(host).matches())
            throw new IllegalArgumentException("database-settings.host is empty or has invalid characters");
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("database-settings.port must be between 1 and 65535");
        if (!DATABASE.matcher(database).matches())
            throw new IllegalArgumentException("database-settings.database is empty or has invalid characters");

        this.url = String.format("jdbc:mysql://%s:%d/%s?autoReconnect=true&connectTimeout=5000&socketTimeout=15000"
                        + "&allowLoadLocalInfile=false&allowUrlInLocalInfile=false&allowMultiQueries=false",
                host, port, database);
        this.user = section.getString("user");
        this.password = section.getString("password");
    }

    @Override
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                throw new SQLException("MySQL driver not found", e);
            }
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    @Override
    public synchronized void closeConnection() {
        if (connection == null)
            return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }

}
