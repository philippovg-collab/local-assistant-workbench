package com.example.demo.infrastructure.material;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.reference.port.ReferenceUsageRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresReferenceUsageRepository implements ReferenceUsageRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresReferenceUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean projectHasMaterialReferences(String key) {
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM materials WHERE project_key = ?)",
                Boolean.class,
                key
            );
            return Boolean.TRUE.equals(exists);
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "reference_project.storage_read_failed",
                "Unable to check reference project usage in PostgreSQL",
                exception
            );
        }
    }
}
