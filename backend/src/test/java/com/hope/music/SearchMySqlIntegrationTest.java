package com.hope.music;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Invoked only by the disposable server runner, against its new, dedicated database. */
class SearchMySqlIntegrationTest extends SearchHttpTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("DB_URL");
        if (!"true".equals(System.getenv("LVDONG_ISOLATED_MYSQL")) || url == null
                || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/lvdong_ephemeral_regression\\?.*")) {
            throw new IllegalStateException("Requires a dedicated temporary MySQL database and isolation marker");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "root");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Override
    protected void assertIsolatedDatabase(String url) {
        assertThat(url).isEqualTo(System.getenv("DB_URL"));
    }
}
