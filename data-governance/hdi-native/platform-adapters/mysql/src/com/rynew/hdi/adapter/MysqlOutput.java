package com.rynew.hdi.adapter;

/** Platform driver/dialect selection only. All output processing remains in the original HDI JDBC implementation. */
public final class MysqlOutput extends com.hikvision.idatafusion.etl.adapter.output.jdbc.JdbcOutput {
    @Override public String getDriverClassName() { return "org.mariadb.jdbc.Driver"; }
    @Override public String getJdbcUrlPrefix() { return "jdbc:mysql://"; }
    @Override public String getCaseChar() { return "`"; }
}
