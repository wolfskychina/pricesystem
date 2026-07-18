package com.bank.trading.common.persistence.config;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * MyBatis 配置类，负责 Mapper 扫描与多数据库方言适配。
 *
 * <p>本系统需要同时支持 SQLite（开发/测试）、PostgreSQL（生产）、MySQL（可选）三种数据库，
 * 通过 {@link DatabaseIdProvider} 机制实现 SQL 的多数据库兼容。Mapper XML 中可使用
 * {@code databaseId} 属性为不同数据库编写差异化 SQL，或在注解 SQL 中通过
 * {@code <if test="_databaseId == 'sqlite'">} 等方式分支。</p>
 *
 * <p><b>多数据库支持的业务背景：</b>开发阶段使用 SQLite 零配置快速启动，
 * 生产环境使用 PostgreSQL 获得高可用与高性能，部分场景可能用 MySQL。
 * 同一套代码需在三种数据库上运行，因此持久层必须做方言适配。</p>
 */
@Configuration
@MapperScan(basePackages = "com.bank.trading.**.mapper")
public class MyBatisConfig {

    /**
     * 注册数据库厂商 ID 提供者，将 JDBC 元数据中的数据库名映射为短标识符。
     *
     * <p>映射关系：
     * <ul>
     *   <li>SQLite → "sqlite"</li>
     *   <li>PostgreSQL → "postgresql"</li>
     *   <li>MySQL → "mysql"</li>
     * </ul>
     * MyBatis 在执行 SQL 时会将当前数据库的标识符注入 {@code _databaseId} 变量，
     * Mapper 可据此选择对应方言的 SQL 片段（如 SQLite 用 {@code INSERT OR IGNORE}，
     * PostgreSQL 用 {@code ON CONFLICT DO NOTHING}）。</p>
     *
     * @return 配置好的数据库厂商 ID 提供者
     */
    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        // 键为 JDBC 元数据中的产品名前缀（不区分大小写），值为 MyBatis 中的 databaseId
        properties.setProperty("SQLite", "sqlite");
        properties.setProperty("PostgreSQL", "postgresql");
        properties.setProperty("MySQL", "mysql");
        provider.setProperties(properties);
        return provider;
    }
}
