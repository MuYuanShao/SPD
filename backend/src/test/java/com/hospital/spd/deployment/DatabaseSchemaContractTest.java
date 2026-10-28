package com.hospital.spd.deployment;

import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Makes missing runtime objects or authorization definitions observable before serving business requests. */
class DatabaseSchemaContractTest {
    @Test
    void rejectsMissingInvoiceTableEvenWhenMigrationHistoryWasValidated() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(contains("supplier_invoice"))).thenThrow(new SQLException("missing table", "42S02"));
        assertThatThrownBy(() -> DatabaseSchemaContract.verify(connection))
                .isInstanceOf(SQLException.class).hasMessageContaining("补偿迁移");
        verify(statement, never()).executeUpdate(anyString());
    }

    @Test
    void requiresMetadataPresenceWithoutRegrantingRolesOrEnablingDisabledPermissions() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet permissions = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(permissions);
        when(permissions.next()).thenReturn(true, true, true, true, false);
        when(permissions.getString(1)).thenReturn("purchase-management", "purchase-order:create", "purchase-order:approve", "invoice-management");
        DatabaseSchemaContract.verify(connection);
        verify(statement).executeQuery(argThat(sql -> sql.contains("FROM sys_permission") && !sql.contains("status=1")));
        verify(statement, never()).executeUpdate(anyString());
    }
}
