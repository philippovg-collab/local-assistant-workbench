package com.example.demo.service.eval;

import com.example.demo.model.ChatSource;
import com.example.demo.model.EvidenceLocator;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.eval.AnswerCitation;
import com.example.demo.model.eval.AnswerClaim;
import com.example.demo.model.eval.EvalStructuredAnswer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EvalCitationResolver {

    public EvalStructuredAnswer resolve(EvalStructuredAnswer structuredAnswer, List<ChatSource> sources) {
        List<ChatSource> safeSources = sources == null ? List.of() : sources;
        List<AnswerClaim> resolvedClaims = new ArrayList<>();
        for (AnswerClaim claim : structuredAnswer.claims()) {
            List<AnswerCitation> resolvedCitations = new ArrayList<>();
            for (AnswerCitation citation : claim.citations()) {
                int index = citation.sourceId() == null ? -1 : citation.sourceId() - 1;
                if (index < 0 || index >= safeSources.size()) {
                    throw new EvalCitationResolutionException("Citation sourceId " + citation.sourceId() + " does not match a chat source");
                }
                resolvedCitations.add(new AnswerCitation(citation.sourceId(), locatorFor(safeSources.get(index))));
            }
            resolvedClaims.add(new AnswerClaim(claim.claimId(), claim.text(), resolvedCitations));
        }
        return new EvalStructuredAnswer(structuredAnswer.answer(), structuredAnswer.finalMode(), resolvedClaims);
    }

    private EvidenceLocator locatorFor(ChatSource source) {
        if (source.evidenceLocator() != null) {
            return source.evidenceLocator();
        }
        MaterialMetadataSnapshot metadata = source.metadata() == null ? MaterialMetadataSnapshot.empty() : source.metadata();
        return new EvidenceLocator(
            null,
            source.materialId(),
            metadata.documentNumber(),
            metadata.versionLabel(),
            null,
            null,
            source.chunkIndex(),
            source.page(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
