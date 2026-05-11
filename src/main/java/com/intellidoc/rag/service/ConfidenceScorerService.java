package com.intellidoc.rag.service;

import com.intellidoc.search.service.QueryEmbeddingService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConfidenceScorerService {

    private final QueryEmbeddingService queryEmbeddingService;

    public ConfidenceScorerService(QueryEmbeddingService queryEmbeddingService) {
        this.queryEmbeddingService = queryEmbeddingService;
    }

    public ConfidenceScore score(
            String query,
            String answer,
            ContextAssemblyService.AssembledContext assembledContext,
            CitationExtractorService.CitationExtractionResult citationExtractionResult) {
        double retrievalQuality = normalizedRerankerScore(assembledContext.sources());
        double sourceCoverage = sourceCoverage(answer, citationExtractionResult);
        double sourceAgreement = sourceAgreement(citationExtractionResult);
        double queryAnswerSimilarity = queryAnswerSimilarity(query, answer);

        double score = clamp(
                (retrievalQuality * 0.40)
                        + (sourceCoverage * 0.30)
                        + (sourceAgreement * 0.20)
                        + (queryAnswerSimilarity * 0.10));

        return new ConfidenceScore(
                score,
                retrievalQuality,
                sourceCoverage,
                sourceAgreement,
                queryAnswerSimilarity,
                band(score));
    }

    private double normalizedRerankerScore(List<ContextAssemblyService.SourceContext> sources) {
        if (sources.isEmpty()) {
            return 0.0;
        }
        double topScore = sources.stream()
                .map(ContextAssemblyService.SourceContext::rerankerScore)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElseGet(() -> sources.stream()
                        .map(ContextAssemblyService.SourceContext::searchScore)
                        .filter(value -> value != null)
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(0.0));
        return clamp(topScore / 4.0);
    }

    private double sourceCoverage(String answer, CitationExtractorService.CitationExtractionResult extractionResult) {
        if (answer == null || answer.isBlank()) {
            return 0.0;
        }
        String[] sentences = answer.split("(?<=[.!?])\\s+");
        int substantiveSentences = 0;
        for (String sentence : sentences) {
            if (sentence != null && sentence.trim().length() > 20) {
                substantiveSentences++;
            }
        }
        if (substantiveSentences == 0) {
            return 0.0;
        }
        return clamp((double) extractionResult.citedSentenceCount() / substantiveSentences);
    }

    private double sourceAgreement(CitationExtractorService.CitationExtractionResult extractionResult) {
        if (extractionResult.uniqueSourceCount() >= 2) {
            return 1.0;
        }
        if (extractionResult.uniqueSourceCount() == 1) {
            return 0.5;
        }
        return 0.0;
    }

    private double queryAnswerSimilarity(String query, String answer) {
        if (answer == null || answer.isBlank()) {
            return 0.0;
        }
        List<Float> queryVector = queryEmbeddingService.embedText(query);
        List<Float> answerVector = queryEmbeddingService.embedText(answer);
        return clamp((cosineSimilarity(queryVector, answerVector) + 1.0) / 2.0);
    }

    private double cosineSimilarity(List<Float> left, List<Float> right) {
        int size = Math.min(left.size(), right.size());
        if (size == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < size; index++) {
            double leftValue = left.get(index);
            double rightValue = right.get(index);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private String band(double score) {
        if (score >= 0.8) {
            return "HIGH";
        }
        if (score >= 0.5) {
            return "MODERATE";
        }
        return "LOW";
    }

    public record ConfidenceScore(
            double score,
            double retrievalQuality,
            double sourceCoverage,
            double sourceAgreement,
            double queryAnswerSimilarity,
            String band) {
    }
}
