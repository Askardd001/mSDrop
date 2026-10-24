package dev.msdrop.database.manager;

import dev.msdrop.database.datasource.DataSource;
import dev.msdrop.database.datasource.impl.MySQL;
import dev.msdrop.database.table.UsersTable;

public class DatabaseManager {

    private final DataSource dataSource = new MySQL();

    private UsersTable usersTable;

    public boolean load() {
        usersTable = new UsersTable(dataSource);
        return usersTable.createTable();
    }

    public void close() {
        dataSource.closeConnection();
    }

    public UsersTable getUsersTable() {
        return usersTable;
    }

}
