package com.intellidoc.ingestion.service;

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient;
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzeResult;
import com.azure.ai.formrecognizer.documentanalysis.models.BoundingRegion;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentParagraph;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentSpan;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentTable;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentTableCell;
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentTableCellKind;
import com.azure.ai.formrecognizer.documentanalysis.models.ParagraphRole;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.BinaryData;
import com.intellidoc.admin.storage.DocumentStorageService;
import com.intellidoc.config.IntelliDocProperties;
import com.intellidoc.ingestion.model.DocumentLayoutBlock;
import com.intellidoc.ingestion.model.DocumentLayoutBlockType;
import com.intellidoc.ingestion.model.IngestionWorkItem;
import com.intellidoc.ingestion.model.ParsedDocumentLayout;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnBean(DocumentAnalysisClient.class)
public class AzureDocumentIntelligenceLayoutExtractionService implements DocumentLayoutExtractionService {

    private static final Logger log = LoggerFactory.getLogger(AzureDocumentIntelligenceLayoutExtractionService.class);

    private final DocumentAnalysisClient documentAnalysisClient;
    private final DocumentStorageService documentStorageService;
    private final IntelliDocProperties properties;

    public AzureDocumentIntelligenceLayoutExtractionService(
            DocumentAnalysisClient documentAnalysisClient,
            DocumentStorageService documentStorageService,
            IntelliDocProperties properties) {
        this.documentAnalysisClient = documentAnalysisClient;
        this.documentStorageService = documentStorageService;
        this.properties = properties;
    }

    @Override
    public ParsedDocumentLayout extract(IngestionWorkItem workItem) {
        try (InputStream inputStream = documentStorageService.openStream(
                workItem.blobPath(),
                workItem.blobVersionId())) {
            String modelId = properties.getIngestion().getDocumentIntelligence().getModelId();
            long documentLength = workItem.documentVersion().getFileSizeBytes();
            if (documentLength <= 0) {
                throw new NonRetryableIngestionException(
                        "Document version %s does not have a valid file size for Document Intelligence analysis."
                                .formatted(workItem.documentVersionId()));
            }
            AnalyzeResult result = documentAnalysisClient
                    .beginAnalyzeDocument(modelId, BinaryData.fromStream(inputStream, documentLength))
                    .getFinalResult();

            ParsedDocumentLayout parsedLayout = mapAnalyzeResult(result);
            log.info(
                    "Document Intelligence extracted {} blocks (paragraphs={}, tables={}, pages={}) for document {} version {} using model {}",
                    parsedLayout.blocks().size(),
                    parsedLayout.paragraphCount(),
                    parsedLayout.tableCount(),
                    parsedLayout.totalPages(),
                    workItem.documentId(),
                    workItem.versionNumber(),
                    modelId);
            return parsedLayout;
        } catch (IOException exception) {
            throw new NonRetryableIngestionException(
                    "Failed to load document binary for analysis from blob path %s: %s"
                            .formatted(workItem.blobPath(), exception.getMessage()));
        } catch (HttpResponseException exception) {
            int statusCode = exception.getResponse() == null ? 0 : exception.getResponse().getStatusCode();
            throw new IllegalStateException(
                    "Document Intelligence request failed with status %s: %s"
                            .formatted(statusCode, exception.getMessage()),
                    exception);
        }
    }

    private ParsedDocumentLayout mapAnalyzeResult(AnalyzeResult result) {
        List<BlockCandidate> candidates = new ArrayList<>();
        List<DocumentParagraph> paragraphs = result.getParagraphs() == null ? List.of() : result.getParagraphs();
        List<DocumentTable> tables = result.getTables() == null ? List.of() : result.getTables();

        for (DocumentParagraph paragraph : paragraphs) {
            String content = normalizeContent(paragraph.getContent());
            if (!StringUtils.hasText(content)) {
                continue;
            }

            Integer startOffset = firstOffset(paragraph.getSpans());
            Integer endOffset = endOffset(paragraph.getSpans());
            candidates.add(new BlockCandidate(
                    startOffset == null ? Integer.MAX_VALUE : startOffset,
                    mapParagraphRole(paragraph.getRole()),
                    content,
                    firstPageNumber(paragraph.getBoundingRegions()),
                    paragraph.getRole() == null ? null : paragraph.getRole().toString(),
                    startOffset,
                    endOffset,
                    null,
                    null));
        }

        for (DocumentTable table : tables) {
            String content = serializeTableToMarkdown(table);
            Integer startOffset = firstOffset(table.getSpans());
            Integer endOffset = endOffset(table.getSpans());
            candidates.add(new BlockCandidate(
                    startOffset == null ? Integer.MAX_VALUE : startOffset,
                    DocumentLayoutBlockType.TABLE,
                    content,
                    firstPageNumber(table.getBoundingRegions()),
                    null,
                    startOffset,
                    endOffset,
                    table.getRowCount(),
                    table.getColumnCount()));
        }

        candidates.sort(Comparator.comparingInt(BlockCandidate::sortOffset)
                .thenComparing(candidate -> candidate.pageNumber() == null ? Integer.MAX_VALUE : candidate.pageNumber()));

        List<DocumentLayoutBlock> blocks = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            BlockCandidate candidate = candidates.get(index);
            blocks.add(new DocumentLayoutBlock(
                    index,
                    candidate.blockType(),
                    candidate.content(),
                    candidate.pageNumber(),
                    candidate.paragraphRole(),
                    candidate.startOffset(),
                    candidate.endOffset(),
                    candidate.tableRowCount(),
                    candidate.tableColumnCount()));
        }

        int totalPages = result.getPages() == null ? 0 : result.getPages().size();
        return new ParsedDocumentLayout(totalPages, paragraphs.size(), tables.size(), blocks);
    }

    private DocumentLayoutBlockType mapParagraphRole(ParagraphRole role) {
        if (role == null) {
            return DocumentLayoutBlockType.PARAGRAPH;
        }

        String normalizedRole = role.toString().toUpperCase(Locale.ROOT);
        return switch (normalizedRole) {
            case "TITLE" -> DocumentLayoutBlockType.TITLE;
            case "SECTION_HEADING", "SECTIONHEADER" -> DocumentLayoutBlockType.SECTION_HEADING;
            case "PAGE_HEADER", "PAGEHEADER" -> DocumentLayoutBlockType.PAGE_HEADER;
            case "PAGE_FOOTER", "PAGEFOOTER" -> DocumentLayoutBlockType.PAGE_FOOTER;
            case "PAGE_NUMBER", "PAGENUMBER" -> DocumentLayoutBlockType.PAGE_NUMBER;
            case "FOOTNOTE" -> DocumentLayoutBlockType.FOOTNOTE;
            case "FORMULA_BLOCK", "FORMULABLOCK" -> DocumentLayoutBlockType.FORMULA_BLOCK;
            default -> DocumentLayoutBlockType.PARAGRAPH;
        };
    }

    private Integer firstPageNumber(List<BoundingRegion> boundingRegions) {
        if (boundingRegions == null || boundingRegions.isEmpty()) {
            return null;
        }
        return boundingRegions.getFirst().getPageNumber();
    }

    private Integer firstOffset(List<DocumentSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return null;
        }
        return spans.stream()
                .filter(Objects::nonNull)
                .map(DocumentSpan::getOffset)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private Integer endOffset(List<DocumentSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return null;
        }
        return spans.stream()
                .filter(Objects::nonNull)
                .map(span -> span.getOffset() + span.getLength())
                .max(Integer::compareTo)
                .orElse(null);
    }

    private String serializeTableToMarkdown(DocumentTable table) {
        int rowCount = table.getRowCount();
        int columnCount = table.getColumnCount();
        List<List<String>> rows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            List<String> row = new ArrayList<>();
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                row.add("");
            }
            rows.add(row);
        }

        boolean hasHeaderRow = false;
        List<DocumentTableCell> cells = table.getCells() == null ? List.of() : table.getCells();
        for (DocumentTableCell cell : cells) {
            String content = normalizeContent(cell.getContent());
            if (!StringUtils.hasText(content)) {
                content = "";
            }
            rows.get(cell.getRowIndex()).set(cell.getColumnIndex(), escapeMarkdownPipes(content));
            if (cell.getKind() == DocumentTableCellKind.COLUMN_HEADER || cell.getKind() == DocumentTableCellKind.ROW_HEADER) {
                hasHeaderRow = true;
            }
        }

        StringBuilder markdown = new StringBuilder();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            markdown.append("| ");
            markdown.append(String.join(" | ", row));
            markdown.append(" |");

            if (rowIndex == 0 && (hasHeaderRow || rows.size() == 1)) {
                markdown.append(System.lineSeparator());
                markdown.append("| ");
                markdown.append(String.join(" | ", row.stream().map(cell -> "---").toList()));
                markdown.append(" |");
            }

            if (rowIndex < rows.size() - 1) {
                markdown.append(System.lineSeparator());
            }
        }

        return markdown.toString();
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        return content.trim().replaceAll("\\s+", " ");
    }

    private String escapeMarkdownPipes(String value) {
        return value.replace("|", "\\|");
    }

    private record BlockCandidate(
            int sortOffset,
            DocumentLayoutBlockType blockType,
            String content,
            Integer pageNumber,
            String paragraphRole,
            Integer startOffset,
            Integer endOffset,
            Integer tableRowCount,
            Integer tableColumnCount) {
    }
}
