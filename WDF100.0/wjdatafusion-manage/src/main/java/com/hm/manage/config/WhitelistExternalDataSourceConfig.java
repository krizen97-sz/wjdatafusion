package com.hm.manage.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class WhitelistExternalDataSourceConfig
{
    @Bean(name = "whitelistPostgresDataSource")
    public DataSource whitelistPostgresDataSource(WhitelistPostgresProperties properties)
    {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        return dataSource;
    }

    @Bean(name = "whitelistPostgresJdbcTemplate")
    public JdbcTemplate whitelistPostgresJdbcTemplate(@Qualifier("whitelistPostgresDataSource") DataSource dataSource)
    {
        return new JdbcTemplate(dataSource);
    }
}
