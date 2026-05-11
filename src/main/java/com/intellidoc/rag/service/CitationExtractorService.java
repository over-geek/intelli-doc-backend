package com.intellidoc.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CitationExtractorService {

    private static final Pattern SOURCE_PATTERN = Pattern.compile("\\[SOURCE\\s+(\\d+)]");

    public CitationExtractionResult extract(
            String answer,
            ContextAssemblyService.AssembledContext assembledContext) {
        Set<Integer> usedSourceNumbers = new LinkedHashSet<>();
        Matcher matcher = SOURCE_PATTERN.matcher(answer == null ? "" : answer);
        while (matcher.find()) {
            usedSourceNumbers.add(Integer.parseInt(matcher.group(1)));
        }

        List<CitationView> citations = assembledContext.sources().stream()
                .filter(source -> usedSourceNumbers.contains(source.sourceNumber()))
                .map(source -> new CitationView(
                        "[SOURCE %s]".formatted(source.sourceNumber()),
                        source.chunkId(),
                        source.documentId(),
                        source.documentVersionId(),
                        source.documentTitle(),
                        source.pageNumber(),
                        source.sectionHeading(),
                        excerpt(source.content()),
                        source.searchScore(),
                        source.rerankerScore(),
                        source.sourceNumber()))
                .toList();

        return new CitationExtractionResult(citations, countCitedSentences(answer), usedSourceNumbers.size());
    }

    private String excerpt(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 280) {
            return normalized;
        }
        return normalized.substring(0, 277) + "...";
    }

    private int countCitedSentences(String answer) {
        if (answer == null || answer.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String sentence : answer.split("(?<=[.!?])\\s+")) {
            if (SOURCE_PATTERN.matcher(sentence).find()) {
                count++;
            }
        }
        return count;
    }

    public record CitationExtractionResult(
            List<CitationView> citations,
            int citedSentenceCount,
            int uniqueSourceCount) {
    }

    public record CitationView(
            String marker,
            String chunkId,
            String documentId,
            String documentVersionId,
            String documentTitle,
            Integer pageNumber,
            String sectionHeading,
            String excerpt,
            Double searchScore,
            Double rerankerScore,
            int displayOrder) {
    }
}
