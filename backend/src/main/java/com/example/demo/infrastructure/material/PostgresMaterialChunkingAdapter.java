package com.example.demo.infrastructure.material;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialSegment;
import com.example.demo.service.material.port.MaterialChunkingRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialChunkingAdapter extends PostgresMaterialJdbcSupport implements MaterialChunkingRepository {

    PostgresMaterialChunkingAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }

    @Override
    public List<StoredMaterialChunk> findChunks(String materialId) {
        return chunkDao.findChunks(materialId);
    }

    @Override
    public Map<String, List<StoredMaterialChunk>> findChunksByMaterialIds(Collection<String> materialIds) {
        return chunkDao.findChunksByMaterialIds(materialIds);
    }

    @Override
    public List<StoredMaterialSegment> findSegments(String materialId) {
        return chunkDao.findSegments(materialId);
    }

    @Override
    public String findChunkProfile(String materialId) {
        try {
            String chunkProfile = jdbcTemplate.query(
                "SELECT chunk_profile FROM materials WHERE id = ? LIMIT 1",
                (resultSet, rowNum) -> resultSet.getString("chunk_profile"),
                UUID.fromString(materialId)
            ).stream().findFirst().orElse(null);
            return chunkProfile == null ? ChunkProfile.FIXED_V1.propertyValue() : chunkProfile;
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_read_failed",
                "Unable to load material chunk profile from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void replaceChunking(
        String materialId,
        String chunkProfile,
        List<StoredMaterialChunk> chunks,
        List<StoredMaterialSegment> segments,
        Instant updatedAt
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (!recordDao.lockMaterialIfPresent(materialId)) {
                    return;
                }
                jdbcTemplate.update("DELETE FROM material_segments WHERE material_id = ?", UUID.fromString(materialId));
                jdbcTemplate.update("DELETE FROM material_chunks WHERE material_id = ?", UUID.fromString(materialId));
                chunkDao.insertRawChunks(materialId, chunks);
                chunkDao.insertSegments(materialId, segments);
                jdbcTemplate.update(
                    "UPDATE materials SET chunk_profile = ?, updated_at = ? WHERE id = ?",
                    chunkProfile,
                    Timestamp.from(updatedAt),
                    UUID.fromString(materialId)
                );
            });
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_write_failed",
                "Unable to replace material chunking in PostgreSQL",
                exception
            );
        }
    }
}
