package com.intellidoc.chat.model;

import com.intellidoc.chat.model.ChatMessageRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSessionEntity session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChatMessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> citations = new ArrayList<>();

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "token_count_prompt", nullable = false)
    private int tokenCountPrompt;

    @Column(name = "token_count_completion", nullable = false)
    private int tokenCountCompletion;

    @Column(name = "retrieval_count", nullable = false)
    private int retrievalCount;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ChatSessionEntity getSession() {
        return session;
    }

    public void setSession(ChatSessionEntity session) {
        this.session = session;
    }

    public ChatMessageRole getRole() {
        return role;
    }

    public void setRole(ChatMessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getCitations() {
        return citations;
    }

    public void setCitations(List<String> citations) {
        this.citations = citations == null ? new ArrayList<>() : new ArrayList<>(citations);
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public int getTokenCountPrompt() {
        return tokenCountPrompt;
    }

    public void setTokenCountPrompt(int tokenCountPrompt) {
        this.tokenCountPrompt = tokenCountPrompt;
    }

    public int getTokenCountCompletion() {
        return tokenCountCompletion;
    }

    public void setTokenCountCompletion(int tokenCountCompletion) {
        this.tokenCountCompletion = tokenCountCompletion;
    }

    public int getRetrievalCount() {
        return retrievalCount;
    }

    public void setRetrievalCount(int retrievalCount) {
        this.retrievalCount = retrievalCount;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (citations == null) {
            citations = new ArrayList<>();
        }
    }
}
