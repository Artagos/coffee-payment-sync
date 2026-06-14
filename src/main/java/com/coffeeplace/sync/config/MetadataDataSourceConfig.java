package com.coffeeplace.sync.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.coffeeplace.sync.repository.metadata",
    entityManagerFactoryRef = "metadataEntityManagerFactory",
    transactionManagerRef = "metadataTransactionManager"
)
public class MetadataDataSourceConfig {

    @Primary
    @Bean(name = "metadataDataSource")
    public DataSource metadataDataSource(
            @Value("${payment.datasource.metadata.url}") String url,
            @Value("${payment.datasource.metadata.username}") String username,
            @Value("${payment.datasource.metadata.password}") String password) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    @Primary
    @Bean(name = "metadataEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean metadataEntityManagerFactory(
            @Qualifier("metadataDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.coffeeplace.sync.model.metadata");
        em.setPersistenceUnitName("metadata");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Properties props = new Properties();
        props.setProperty("hibernate.hbm2ddl.auto", "update");
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.setProperty("hibernate.show_sql", "false");
        em.setJpaProperties(props);

        return em;
    }

    @Primary
    @Bean(name = "metadataTransactionManager")
    public PlatformTransactionManager metadataTransactionManager(
            @Qualifier("metadataEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }
}
