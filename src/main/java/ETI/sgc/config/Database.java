package ETI.sgc.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

public class Database {

    public static Jdbi jdbi;
    private static HikariDataSource dataSource;

    public static void init() {
        init(AppConfig.load());
    }

    public static void init(AppConfig appConfig) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(appConfig.get("DB_URL", "jdbc:postgresql://localhost:5432/wepa"));
        hikariConfig.setUsername(appConfig.get("DB_USER", "postgres"));
        hikariConfig.setPassword(appConfig.get("DB_PASSWORD", "123"));
        hikariConfig.setMaximumPoolSize(appConfig.getInt("DB_POOL_MAX_SIZE", 10));
        hikariConfig.setMinimumIdle(appConfig.getInt("DB_POOL_MIN_IDLE", 2));
        hikariConfig.setConnectionTimeout(appConfig.getLong("DB_CONNECTION_TIMEOUT_MS", 30000));
        hikariConfig.setIdleTimeout(appConfig.getLong("DB_IDLE_TIMEOUT_MS", 600000));
        hikariConfig.setMaxLifetime(appConfig.getLong("DB_MAX_LIFETIME_MS", 1800000));
        hikariConfig.setPoolName("wepa-db-pool");
        hikariConfig.addDataSourceProperty("reWriteBatchedInserts", "true");

        dataSource = new HikariDataSource(hikariConfig);

        if (appConfig.getBoolean("DB_MIGRATIONS_ENABLED", true)) {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .schemas("public")
                    .load()
                    .migrate();
        }

        jdbi = Jdbi.create(dataSource);
        jdbi.installPlugin(new SqlObjectPlugin());
    }

    public static void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
