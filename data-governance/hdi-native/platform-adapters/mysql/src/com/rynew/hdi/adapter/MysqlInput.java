package com.rynew.hdi.adapter;

/** Platform driver/dialect selection only. All input processing remains in the original HDI JDBC implementation. */
public final class MysqlInput extends com.hikvision.idatafusion.etl.adapter.input.jdbc.JdbcInput {
    @Override public String getDriverClassName() { return "org.mariadb.jdbc.Driver"; }
    @Override public String getJdbcUrlPrefix() { return "jdbc:mysql://"; }
    @Override public String getCaseChar() { return "`"; }
}
