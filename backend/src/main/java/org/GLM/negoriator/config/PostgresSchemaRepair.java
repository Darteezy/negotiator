package org.GLM.negoriator.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresSchemaRepair implements ApplicationRunner {

	private final DataSource dataSource;
	private final JdbcTemplate jdbcTemplate;

	public PostgresSchemaRepair(DataSource dataSource, JdbcTemplate jdbcTemplate) {
		this.dataSource = dataSource;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(org.springframework.boot.ApplicationArguments args) throws Exception {
		if (!isPostgreSql()) {
			return;
		}

		jdbcTemplate.execute("""
			ALTER TABLE negotiation_strategy_changes
			DROP CONSTRAINT IF EXISTS negotiation_strategy_changes_trigger_type_check
			""");

		jdbcTemplate.execute("""
			ALTER TABLE negotiation_strategy_changes
			ADD CONSTRAINT negotiation_strategy_changes_trigger_type_check
			CHECK (
				trigger_type IN (
					'INITIAL_SELECTION',
					'DEADLINE_PRESSURE',
					'STALLED_NEGOTIATION',
					'RECIPROCAL_PROGRESS',
					'AI_RECOMMENDATION',
					'MANUAL_CONFIGURATION'
				)
			)
			""");
	}

	private boolean isPostgreSql() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			DatabaseMetaData metaData = connection.getMetaData();
			return metaData.getDatabaseProductName() != null
				&& metaData.getDatabaseProductName().toLowerCase().contains("postgresql");
		}
	}
}