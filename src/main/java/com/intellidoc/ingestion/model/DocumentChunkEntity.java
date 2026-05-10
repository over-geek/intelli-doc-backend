package com.intellidoc.ingestion.model;

import com.intellidoc.admin.model.DocumentEntity;
import com.intellidoc.admin.model.DocumentVersionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "document_chunk")
public class DocumentChunkEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_version_id", nullable = false)
    private DocumentVersionEntity documentVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "section_heading", length = 500)
    private String sectionHeading;

    @Column(name = "start_char_offset")
    private Integer startCharOffset;

    @Column(name = "end_char_offset")
    private Integer endCharOffset;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "embedding_vector", columnDefinition = "jsonb")
    private List<Float> embeddingVector = new ArrayList<>();

    @Column(name = "ai_search_doc_id", length = 255)
    private String aiSearchDocId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DocumentVersionEntity getDocumentVersion() {
        return documentVersion;
    }

    public void setDocumentVersion(DocumentVersionEntity documentVersion) {
        this.documentVersion = documentVersion;
    }

    public DocumentEntity getDocument() {
        return document;
    }

    public void setDocument(DocumentEntity document) {
        this.document = document;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getSectionHeading() {
        return sectionHeading;
    }

    public void setSectionHeading(String sectionHeading) {
        this.sectionHeading = sectionHeading;
    }

    public Integer getStartCharOffset() {
        return startCharOffset;
    }

    public void setStartCharOffset(Integer startCharOffset) {
        this.startCharOffset = startCharOffset;
    }

    public Integer getEndCharOffset() {
        return endCharOffset;
    }

    public void setEndCharOffset(Integer endCharOffset) {
        this.endCharOffset = endCharOffset;
    }

    public List<Float> getEmbeddingVector() {
        return embeddingVector;
    }

    public void setEmbeddingVector(List<Float> embeddingVector) {
        this.embeddingVector = embeddingVector == null ? new ArrayList<>() : new ArrayList<>(embeddingVector);
    }

    public String getAiSearchDocId() {
        return aiSearchDocId;
    }

    public void setAiSearchDocId(String aiSearchDocId) {
        this.aiSearchDocId = aiSearchDocId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
