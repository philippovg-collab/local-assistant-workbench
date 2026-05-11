package com.example.demo.service.eval;

import com.example.demo.model.eval.CreateEvalDatasetRequest;
import com.example.demo.model.eval.CreateEvalDatasetVersionRequest;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetKind;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.model.eval.UpdateEvalDatasetRequest;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalDatasetMutationService {

    private final EvalDatasetRepository repository;
    private final EvalDatasetLifecycleValidator validator;
    private final EvalDatasetVersioningService versioningService;
    private final Clock clock;

    public EvalDatasetMutationService(
        EvalDatasetRepository repository,
        EvalDatasetLifecycleValidator validator,
        EvalDatasetVersioningService versioningService,
        Clock clock
    ) {
        this.repository = repository;
        this.validator = validator;
        this.versioningService = versioningService;
        this.clock = clock;
    }

    EvalDataset createDataset(CreateEvalDatasetRequest request) {
        Instant now = clock.instant();
        EvalDataset dataset = new EvalDataset(
            UUID.randomUUID().toString(),
            validator.normalizedRequired(request.datasetKey(), "datasetKey"),
            request.kind(),
            StringUtils.hasText(request.version()) ? request.version().trim() : "v1",
            EvalLifecycleStatus.ACTIVE,
            validator.normalizedRequired(request.name(), "name"),
            validator.normalizeOptional(request.description()),
            request.tags(),
            now,
            now
        );
        EvalDataset saved = repository.saveDataset(dataset);
        if (saved.kind() == EvalDatasetKind.CANDIDATE) {
            versioningService.createDatasetVersion(
                saved.id(),
                new CreateEvalDatasetVersionRequest(saved.version(), "Initial dataset version")
            );
        }
        return saved;
    }

    EvalDataset updateDataset(String id, UpdateEvalDatasetRequest request) {
        EvalDataset current = validator.requireDataset(id);
        return repository.saveDataset(new EvalDataset(
            current.id(),
            current.datasetKey(),
            current.kind(),
            current.version(),
            current.status(),
            StringUtils.hasText(request.name()) ? request.name().trim() : current.name(),
            request.description() == null ? current.description() : validator.normalizeOptional(request.description()),
            request.tags() == null ? current.tags() : request.tags(),
            current.createdAt(),
            clock.instant()
        ));
    }

    EvalDataset archiveDataset(String id) {
        EvalDataset current = validator.requireDataset(id);
        return repository.saveDataset(new EvalDataset(
            current.id(),
            current.datasetKey(),
            current.kind(),
            current.version(),
            EvalLifecycleStatus.ARCHIVED,
            current.name(),
            current.description(),
            current.tags(),
            current.createdAt(),
            clock.instant()
        ));
    }
}
