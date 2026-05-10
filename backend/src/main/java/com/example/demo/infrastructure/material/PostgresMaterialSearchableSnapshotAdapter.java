package com.example.demo.infrastructure.material;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.material.SearchableMaterialChunkSnapshot;
import com.example.demo.service.material.SearchableMaterialSnapshot;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.MaterialSearchableSnapshotRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialSearchableSnapshotAdapter
    extends PostgresMaterialJdbcSupport
    implements MaterialSearchableSnapshotRepository {

    PostgresMaterialSearchableSnapshotAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }

    @Override
    public SearchableMaterialSnapshot resolveSearchableSnapshot(String materialId) {
        Optional<StoredMaterialRecord> record = recordDao.findById(materialId);
        if (record.isEmpty() || !recordDao.isSearchable(record.get())) {
            return SearchableMaterialSnapshot.notSearchable(materialId);
        }

        List<SearchableMaterialChunkSnapshot> chunks = chunkDao.findSearchableChunks(materialId);
        StoredMaterialRecord material = record.get();
        return new SearchableMaterialSnapshot(
            material.id(),
            true,
            material.sourceKey(),
            material.title(),
            material.sourceType(),
            material.originalFileName(),
            material.mediaType(),
            material.createdAt(),
            material.updatedAt(),
            material.metadata(),
            chunks
        );
    }

    @Override
    public List<String> findAllSearchableMaterialIds() {
        try {
            return jdbcTemplate.query(
                """
                    SELECT m.id
                    FROM materials m
                    WHERE
                    """ + filterSqlBuilder.retrievalReadyPredicate("m") + """
                    ORDER BY m.updated_at ASC, m.id ASC
                    """,
                (resultSet, rowNum) -> resultSet.getObject("id").toString()
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_read_failed",
                "Unable to enumerate searchable material ids from PostgreSQL",
                exception
            );
        }
    }
}
