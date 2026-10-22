package com.stockpilot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIf("isDockerAvailable")
class StockPilotApplicationTests {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    @DisplayName("Context loads and Flyway migrations execute successfully against PostgreSQL 16 Testcontainer")
    void contextLoadsAndFlywayAppliesMigrations() {
        assertThat(jdbcTemplate).isNotNull();
        Integer categoryCount = jdbcTemplate.queryForObject("SELECT count(*) FROM category", Integer.class);
        Integer warehouseCount = jdbcTemplate.queryForObject("SELECT count(*) FROM warehouse", Integer.class);
        Integer productCount = jdbcTemplate.queryForObject("SELECT count(*) FROM product", Integer.class);
        Integer stockCount = jdbcTemplate.queryForObject("SELECT count(*) FROM stock_level", Integer.class);
        Integer ledgerCount = jdbcTemplate.queryForObject("SELECT count(*) FROM inventory_ledger", Integer.class);

        assertThat(categoryCount).isNotNull().isEqualTo(4);
        assertThat(warehouseCount).isNotNull().isEqualTo(3);
        assertThat(productCount).isNotNull().isEqualTo(5);
        assertThat(stockCount).isNotNull().isEqualTo(9);
        assertThat(ledgerCount).isNotNull().isEqualTo(9);
    }
}

