package com.example.demo.infrastructure.material;

import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.SourceTrustLevel;
import com.example.demo.model.VersionSelectionMode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

final class PostgresMaterialFilterSqlBuilder {

    private static final String NO_MATCH_LANGUAGE_CODE = "__INVALID_LANGUAGE__";

    SearchFilterSql buildSearchFilterSql(RetrievalFilters filters) {
        RetrievalFilters safeFilters = filters == null ? RetrievalFilters.empty() : filters;
        if (safeFilters.isEmpty()) {
            return SearchFilterSql.empty();
        }

        return new SearchFilterSql(
            """
                  AND (CAST(? AS text) IS NULL OR LOWER(m.document_number) = ?)
                  AND (? = FALSE OR m.document_type = ANY (?))
                  AND (? = FALSE OR m.document_status = ANY (?))
                  AND (? = FALSE OR m.project_key = ANY (?))
                  AND (? = FALSE OR m.language_code = ANY (?))
                  AND (CAST(? AS date) IS NULL OR m.period_start >= ?)
                  AND (CAST(? AS date) IS NULL OR m.period_start <= ?)
                  AND (CAST(? AS date) IS NULL OR m.period_end >= ?)
                  AND (CAST(? AS date) IS NULL OR m.period_end <= ?)
                  AND (CAST(? AS date) IS NULL OR m.document_date >= ?)
                  AND (CAST(? AS date) IS NULL OR m.document_date <= ?)
                  AND (CAST(? AS text) IS NULL OR LOWER(m.department) = ?)
                  AND (CAST(? AS text) IS NULL OR LOWER(m.project_name) = ? OR m.project_key = ?)
                  AND (CAST(? AS text) IS NULL OR LOWER(m.counterparty) = ?)
                  AND (CAST(? AS text) IS NULL OR LOWER(m.business_status) = ?)
                  AND (CAST(? AS text) IS NULL OR m.language_code = ?)
                  AND (CAST(? AS text) IS NULL OR LOWER(m.version_label) = ?)
                  AND (CAST(? AS text) IS NULL OR m.version_state = ?)
                  AND (CAST(? AS timestamptz) IS NULL OR m.created_at >= ?)
                  AND (CAST(? AS timestamptz) IS NULL OR m.created_at < ?)
                  AND (? = FALSE OR EXISTS (
                        SELECT 1
                        FROM material_tags mt
                        WHERE mt.material_id = m.id
                          AND LOWER(mt.tag_value) = ANY (?)
                  ))
                  AND (? = FALSE OR m.source_trust = ANY (?))
                """,
            PostgresMaterialJdbcSupport.lowerCase(safeFilters.documentNumber()),
            safeFilters.documentTypeNames(),
            safeFilters.documentStatusNames(),
            safeFilters.lowerCaseProjectKeys(),
            safeFilters.languageCodeNames(),
            safeFilters.periodStartFrom(),
            safeFilters.periodStartTo(),
            safeFilters.periodEndFrom(),
            safeFilters.periodEndTo(),
            safeFilters.documentDateFrom(),
            safeFilters.documentDateTo(),
            PostgresMaterialJdbcSupport.lowerCase(safeFilters.department()),
            PostgresMaterialJdbcSupport.lowerCase(safeFilters.project()),
            PostgresMaterialJdbcSupport.lowerCase(safeFilters.counterparty()),
            PostgresMaterialJdbcSupport.lowerCase(safeFilters.businessStatus()),
            languageCodeName(safeFilters.language()),
            versionLabelForFilter(safeFilters),
            versionStateForFilter(safeFilters),
            safeFilters.uploadedAfterInclusive(),
            safeFilters.uploadedBeforeExclusive(),
            safeFilters.lowerCaseTags(),
            safeFilters.sourceTrustMin(),
            sourceTrustLevelsAtOrAbove(safeFilters.sourceTrustMin())
        );
    }

    RetrievalScopeSql buildRetrievalScopeSql(
        KnowledgeScope knowledgeScope,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        KnowledgeScope safeScope = knowledgeScope == null ? KnowledgeScope.empty() : knowledgeScope;
        List<String> documentClasses = safeScope.documentClasses().stream().map(Enum::name).toList();
        List<String> documentTypes = safeScope.documentTypes().stream().map(Enum::name).toList();
        List<String> documentStatuses = safeScope.documentStatuses().stream().map(Enum::name).toList();
        List<String> projectKeys = safeScope.projectKeys().stream()
            .map(PostgresMaterialJdbcSupport::lowerCase)
            .filter(java.util.Objects::nonNull)
            .toList();
        List<String> languageCodes = safeScope.languageCodes().stream().map(Enum::name).toList();
        List<String> tags = safeScope.tags().stream()
            .map(PostgresMaterialJdbcSupport::lowerCase)
            .filter(java.util.Objects::nonNull)
            .toList();
        String workspaceKey = PostgresMaterialJdbcSupport.lowerCase(safeScope.workspaceKey());
        String documentNumber = PostgresMaterialJdbcSupport.lowerCase(safeScope.documentNumber());
        if (documentClasses.isEmpty()
            && documentTypes.isEmpty()
            && documentStatuses.isEmpty()
            && projectKeys.isEmpty()
            && documentNumber == null
            && languageCodes.isEmpty()
            && tags.isEmpty()
            && workspaceKey == null
            && safeScope.periodStartFrom() == null
            && safeScope.periodStartTo() == null
            && safeScope.periodEndFrom() == null
            && safeScope.periodEndTo() == null
            && uploadedAfterInclusive == null
            && uploadedBeforeExclusive == null) {
            return RetrievalScopeSql.empty();
        }

        return new RetrievalScopeSql(
            """
                  AND (? = FALSE OR m.knowledge_document_class = ANY (?))
                  AND (? = FALSE OR m.document_type = ANY (?))
                  AND (? = FALSE OR m.document_status = ANY (?))
                  AND (? = FALSE OR m.project_key = ANY (?))
                  AND (CAST(? AS text) IS NULL OR LOWER(m.document_number) = ?)
                  AND (? = FALSE OR m.language_code = ANY (?))
                  AND (? = FALSE OR EXISTS (
                        SELECT 1
                        FROM material_tags mt
                        WHERE mt.material_id = m.id
                          AND LOWER(mt.tag_value) = ANY (?)
                  ))
                  AND (CAST(? AS text) IS NULL OR m.workspace_key = ?)
                  AND (CAST(? AS date) IS NULL OR m.period_start >= ?)
                  AND (CAST(? AS date) IS NULL OR m.period_start <= ?)
                  AND (CAST(? AS date) IS NULL OR m.period_end >= ?)
                  AND (CAST(? AS date) IS NULL OR m.period_end <= ?)
                  AND (CAST(? AS timestamptz) IS NULL OR m.created_at >= ?)
                  AND (CAST(? AS timestamptz) IS NULL OR m.created_at < ?)
                """,
            documentClasses,
            documentTypes,
            documentStatuses,
            projectKeys,
            documentNumber,
            languageCodes,
            tags,
            workspaceKey,
            safeScope.periodStartFrom(),
            safeScope.periodStartTo(),
            safeScope.periodEndFrom(),
            safeScope.periodEndTo(),
            uploadedAfterInclusive,
            uploadedBeforeExclusive
        );
    }

    int bindSearchFilters(PreparedStatement preparedStatement, int startIndex, SearchFilterSql filterSql) throws SQLException {
        if (filterSql == null || filterSql.sql().isBlank()) {
            return startIndex;
        }
        int parameterIndex = startIndex;
        preparedStatement.setString(parameterIndex++, filterSql.documentNumber());
        preparedStatement.setString(parameterIndex++, filterSql.documentNumber());
        preparedStatement.setBoolean(parameterIndex++, !filterSql.documentTypes().isEmpty());
        PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, parameterIndex++, filterSql.documentTypes());
        preparedStatement.setBoolean(parameterIndex++, !filterSql.documentStatuses().isEmpty());
        PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, parameterIndex++, filterSql.documentStatuses());
        preparedStatement.setBoolean(parameterIndex++, !filterSql.projectKeys().isEmpty());
        PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, parameterIndex++, filterSql.projectKeys());
        preparedStatement.setBoolean(parameterIndex++, !filterSql.languageCodes().isEmpty());
        PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, parameterIndex++, filterSql.languageCodes());
        preparedStatement.setObject(parameterIndex++, filterSql.periodStartFrom());
        preparedStatement.setObject(parameterIndex++, filterSql.periodStartFrom());
        preparedStatement.setObject(parameterIndex++, filterSql.periodStartTo());
        preparedStatement.setObject(parameterIndex++, filterSql.periodStartTo());
        preparedStatement.setObject(parameterIndex++, filterSql.periodEndFrom());
        preparedStatement.setObject(parameterIndex++, filterSql.periodEndFrom());
        preparedStatement.setObject(parameterIndex++, filterSql.periodEndTo());
        preparedStatement.setObject(parameterIndex++, filterSql.periodEndTo());
        preparedStatement.setObject(parameterIndex++, filterSql.documentDateFrom());
        preparedStatement.setObject(parameterIndex++, filterSql.documentDateFrom());
        preparedStatement.setObject(parameterIndex++, filterSql.documentDateTo());
        preparedStatement.setObject(parameterIndex++, filterSql.documentDateTo());
        preparedStatement.setString(parameterIndex++, filterSql.department());
        preparedStatement.setString(parameterIndex++, filterSql.department());
        preparedStatement.setString(parameterIndex++, filterSql.project());
        preparedStatement.setString(parameterIndex++, filterSql.project());
        preparedStatement.setString(parameterIndex++, filterSql.project());
        preparedStatement.setString(parameterIndex++, filterSql.counterparty());
        preparedStatement.setString(parameterIndex++, filterSql.counterparty());
        preparedStatement.setString(parameterIndex++, filterSql.businessStatus());
        preparedStatement.setString(parameterIndex++, filterSql.businessStatus());
        preparedStatement.setString(parameterIndex++, filterSql.language());
        preparedStatement.setString(parameterIndex++, filterSql.language());
        preparedStatement.setString(parameterIndex++, filterSql.versionLabel());
        preparedStatement.setString(parameterIndex++, filterSql.versionLabel());
        preparedStatement.setString(parameterIndex++, filterSql.versionState());
        preparedStatement.setString(parameterIndex++, filterSql.versionState());
        parameterIndex = bindNullableInstant(preparedStatement, parameterIndex, filterSql.uploadedAfterInclusive());
        parameterIndex = bindNullableInstant(preparedStatement, parameterIndex, filterSql.uploadedBeforeExclusive());
        preparedStatement.setBoolean(parameterIndex++, !filterSql.tags().isEmpty());
        PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, parameterIndex++, filterSql.tags());
        preparedStatement.setBoolean(parameterIndex++, !filterSql.sourceTrustLevels().isEmpty());
        PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, parameterIndex++, filterSql.sourceTrustLevels());
        return parameterIndex;
    }

    int bindRetrievalScope(PreparedStatement preparedStatement, int startIndex, RetrievalScopeSql scopeSql) throws SQLException {
        if (scopeSql == null || scopeSql.sql().isBlank()) {
            return startIndex;
        }
        int parameterIndex = startIndex;
        preparedStatement.setBoolean(parameterIndex++, !scopeSql.documentClasses().isEmpty());
        PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, parameterIndex++, scopeSql.documentClasses());
        preparedStatement.setBoolean(parameterIndex++, !scopeSql.documentTypes().isEmpty());
        PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, parameterIndex++, scopeSql.documentTypes());
        preparedStatement.setBoolean(parameterIndex++, !scopeSql.documentStatuses().isEmpty());
        PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, parameterIndex++, scopeSql.documentStatuses());
        preparedStatement.setBoolean(parameterIndex++, !scopeSql.projectKeys().isEmpty());
        PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, parameterIndex++, scopeSql.projectKeys());
        preparedStatement.setString(parameterIndex++, scopeSql.documentNumber());
        preparedStatement.setString(parameterIndex++, scopeSql.documentNumber());
        preparedStatement.setBoolean(parameterIndex++, !scopeSql.languageCodes().isEmpty());
        PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, parameterIndex++, scopeSql.languageCodes());
        preparedStatement.setBoolean(parameterIndex++, !scopeSql.tags().isEmpty());
        PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, parameterIndex++, scopeSql.tags());
        preparedStatement.setString(parameterIndex++, scopeSql.workspaceKey());
        preparedStatement.setString(parameterIndex++, scopeSql.workspaceKey());
        preparedStatement.setObject(parameterIndex++, scopeSql.periodStartFrom());
        preparedStatement.setObject(parameterIndex++, scopeSql.periodStartFrom());
        preparedStatement.setObject(parameterIndex++, scopeSql.periodStartTo());
        preparedStatement.setObject(parameterIndex++, scopeSql.periodStartTo());
        preparedStatement.setObject(parameterIndex++, scopeSql.periodEndFrom());
        preparedStatement.setObject(parameterIndex++, scopeSql.periodEndFrom());
        preparedStatement.setObject(parameterIndex++, scopeSql.periodEndTo());
        preparedStatement.setObject(parameterIndex++, scopeSql.periodEndTo());
        if (scopeSql.uploadedAfterInclusive() == null) {
            preparedStatement.setNull(parameterIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            preparedStatement.setNull(parameterIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            Timestamp uploadedAfter = Timestamp.from(scopeSql.uploadedAfterInclusive());
            preparedStatement.setTimestamp(parameterIndex++, uploadedAfter);
            preparedStatement.setTimestamp(parameterIndex++, uploadedAfter);
        }
        if (scopeSql.uploadedBeforeExclusive() == null) {
            preparedStatement.setNull(parameterIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            preparedStatement.setNull(parameterIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            Timestamp uploadedBefore = Timestamp.from(scopeSql.uploadedBeforeExclusive());
            preparedStatement.setTimestamp(parameterIndex++, uploadedBefore);
            preparedStatement.setTimestamp(parameterIndex++, uploadedBefore);
        }
        return parameterIndex;
    }

    String retrievalReadyPredicate(String alias) {
        return retrievalReadyPredicate(alias, null, null, defaultEffectiveDate());
    }

    String retrievalReadyPredicate(String alias, KnowledgeScope scope, RetrievalFilters filters) {
        RetrievalFilters safeFilters = filters == null ? RetrievalFilters.empty() : filters;
        LocalDate effectiveDate = safeFilters.effectiveDate() == null
            ? defaultEffectiveDate()
            : safeFilters.effectiveDate();
        return retrievalReadyPredicate(alias, scope, safeFilters, effectiveDate);
    }

    String retrievalReadyPredicate(String alias, KnowledgeScope scope, RetrievalFilters filters, LocalDate effectiveDate) {
        RetrievalFilters safeFilters = filters == null ? RetrievalFilters.empty() : filters;
        String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
        StringBuilder predicate = new StringBuilder("  1 = 1\n");
        appendVersionSelectionPredicate(predicate, prefix, safeFilters);
        predicate.append("  AND %sindexing_status IN ('READY', 'PARTIAL_READY')%n".formatted(prefix));
        if (!hasExplicitDocumentStatuses(scope, filters)) {
            predicate.append("  AND %sdocument_status = 'ACTIVE'%n".formatted(prefix));
        }
        if (!hasExplicitPeriodCriteria(scope, filters)) {
            LocalDate safeEffectiveDate = effectiveDate == null ? defaultEffectiveDate() : effectiveDate;
            predicate.append("  AND (%speriod_start IS NULL OR %speriod_start <= %s)%n".formatted(
                prefix,
                prefix,
                dateLiteral(safeEffectiveDate)
            ));
            predicate.append("  AND (%speriod_end IS NULL OR %speriod_end >= %s)%n".formatted(
                prefix,
                prefix,
                dateLiteral(safeEffectiveDate)
            ));
        }
        return predicate.toString();
    }

    private static boolean hasExplicitDocumentStatuses(KnowledgeScope scope, RetrievalFilters filters) {
        boolean scopeHasStatuses = scope != null && !scope.documentStatuses().isEmpty();
        boolean filtersHasStatuses = filters != null && filters.hasExplicitDocumentStatuses();
        return scopeHasStatuses || filtersHasStatuses;
    }

    private static boolean hasExplicitPeriodCriteria(KnowledgeScope scope, RetrievalFilters filters) {
        boolean scopeHasPeriod = scope != null
            && (scope.periodStartFrom() != null
                || scope.periodStartTo() != null
                || scope.periodEndFrom() != null
                || scope.periodEndTo() != null);
        boolean filtersHasPeriod = filters != null && filters.hasExplicitPeriods();
        return scopeHasPeriod || filtersHasPeriod;
    }

    private static String languageCodeName(String rawLanguage) {
        if (rawLanguage == null || rawLanguage.isBlank()) {
            return null;
        }
        try {
            return com.example.demo.model.MaterialLanguageCode.fromValue(rawLanguage).name();
        } catch (IllegalArgumentException exception) {
            return NO_MATCH_LANGUAGE_CODE;
        }
    }

    private static int bindNullableInstant(
        PreparedStatement preparedStatement,
        int parameterIndex,
        Instant value
    ) throws SQLException {
        if (value == null) {
            preparedStatement.setNull(parameterIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            preparedStatement.setNull(parameterIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            return parameterIndex;
        }
        Timestamp timestamp = Timestamp.from(value);
        preparedStatement.setTimestamp(parameterIndex++, timestamp);
        preparedStatement.setTimestamp(parameterIndex++, timestamp);
        return parameterIndex;
    }

    private static String versionLabelForFilter(RetrievalFilters filters) {
        return filters.versionSelectionMode() == VersionSelectionMode.VERSION_LABEL
            ? PostgresMaterialJdbcSupport.lowerCase(filters.versionLabel())
            : null;
    }

    private static String versionStateForFilter(RetrievalFilters filters) {
        return filters.versionSelectionMode() == VersionSelectionMode.VERSION_STATE && filters.versionState() != null
            ? filters.versionState().name()
            : null;
    }

    private static void appendVersionSelectionPredicate(
        StringBuilder predicate,
        String prefix,
        RetrievalFilters filters
    ) {
        VersionSelectionMode mode = filters.versionSelectionMode();
        if (mode == VersionSelectionMode.VERSION_STATE && filters.versionState() != null) {
            predicate.append("  AND %sversion_state = '%s'%n".formatted(prefix, filters.versionState().name()));
            return;
        }
        if (mode == VersionSelectionMode.INCLUDE_HISTORY || mode == VersionSelectionMode.VERSION_LABEL) {
            return;
        }
        predicate.append("  AND %sversion_state = '%s'%n".formatted(prefix, MaterialVersionState.ACTIVE.name()));
    }

    private static LocalDate defaultEffectiveDate() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static String dateLiteral(LocalDate date) {
        return "DATE '" + date + "'";
    }

    private static List<String> sourceTrustLevelsAtOrAbove(SourceTrustLevel minimum) {
        if (minimum == null) {
            return List.of();
        }
        int minimumRank = RetrievalFilters.trustRank(minimum);
        return java.util.Arrays.stream(SourceTrustLevel.values())
            .filter(level -> RetrievalFilters.trustRank(level) >= minimumRank)
            .map(Enum::name)
            .toList();
    }

    record RetrievalScopeSql(
        String sql,
        List<String> documentClasses,
        List<String> documentTypes,
        List<String> documentStatuses,
        List<String> projectKeys,
        String documentNumber,
        List<String> languageCodes,
        List<String> tags,
        String workspaceKey,
        LocalDate periodStartFrom,
        LocalDate periodStartTo,
        LocalDate periodEndFrom,
        LocalDate periodEndTo,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        static RetrievalScopeSql empty() {
            return new RetrievalScopeSql(
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
        }
    }

    record SearchFilterSql(
        String sql,
        String documentNumber,
        List<String> documentTypes,
        List<String> documentStatuses,
        List<String> projectKeys,
        List<String> languageCodes,
        LocalDate periodStartFrom,
        LocalDate periodStartTo,
        LocalDate periodEndFrom,
        LocalDate periodEndTo,
        LocalDate documentDateFrom,
        LocalDate documentDateTo,
        String department,
        String project,
        String counterparty,
        String businessStatus,
        String language,
        String versionLabel,
        String versionState,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive,
        List<String> tags,
        SourceTrustLevel sourceTrustMin,
        List<String> sourceTrustLevels
    ) {
        static SearchFilterSql empty() {
            return new SearchFilterSql(
                "",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of()
            );
        }
    }
}
