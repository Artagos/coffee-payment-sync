package com.coffeeplace.sync.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.coffeeplace.sync.repository.shard",
    entityManagerFactoryRef = "shardEntityManagerFactory",
    transactionManagerRef = "shardTransactionManager"
)
public class ShardDataSourceConfig {

    @Bean(name = "shardRoutingDataSource")
    public DataSource shardRoutingDataSource(
            @Value("${payment.shard.count}") int shardCount,
            @Value("${payment.shard.datasource.host-pattern}") String hostPattern,
            @Value("${payment.shard.datasource.username}") String username,
            @Value("${payment.shard.datasource.password}") String password) {
        Map<Object, Object> targetDataSources = new HashMap<>();
        for (int i = 0; i < shardCount; i++) {
            String url = String.format(hostPattern, i);
            DataSource ds = DataSourceBuilder.create()
                    .url(url)
                    .username(username)
                    .password(password)
                    .driverClassName("org.postgresql.Driver")
                    .build();
            targetDataSources.put(i, ds);
        }

        AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                Integer key = ShardContextHolder.get();
                return key != null ? key : 0;
            }
        };
        routing.setTargetDataSources(targetDataSources);
        routing.setDefaultTargetDataSource(targetDataSources.get(0));
        return routing;
    }

    @Bean(name = "shardEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean shardEntityManagerFactory(
            @Qualifier("shardRoutingDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.coffeeplace.sync.model.shard");
        em.setPersistenceUnitName("shard");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Properties props = new Properties();
        props.setProperty("hibernate.hbm2ddl.auto", "update");
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.setProperty("hibernate.show_sql", "false");
        em.setJpaProperties(props);

        return em;
    }

    @Bean(name = "shardTransactionManager")
    public PlatformTransactionManager shardTransactionManager(
            @Qualifier("shardEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }
}
