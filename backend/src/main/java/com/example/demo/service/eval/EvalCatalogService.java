package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.EvalDatasetDetail;
import com.example.demo.model.eval.EvalDatasetSummary;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EvalCatalogService {

    private final EvalDatasetRepository datasetRepository;

    public EvalCatalogService(EvalDatasetRepository datasetRepository) {
        this.datasetRepository = datasetRepository;
    }

    public List<EvalDatasetSummary> listDatasets() {
        return datasetRepository.findDatasetSummaries();
    }

    public EvalDatasetDetail getDataset(String id) {
        return datasetRepository.findDatasetDetail(id)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_dataset.not_found",
                "Eval dataset '" + id + "' does not exist"
            ));
    }
}
