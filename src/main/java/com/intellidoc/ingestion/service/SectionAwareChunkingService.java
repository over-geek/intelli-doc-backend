package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.model.ChunkCandidate;
import com.intellidoc.ingestion.model.ChunkingResult;
import com.intellidoc.ingestion.model.DocumentLayoutBlock;
import com.intellidoc.ingestion.model.DocumentLayoutBlockType;
import com.intellidoc.ingestion.model.ParsedDocumentLayout;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SectionAwareChunkingService implements ChunkingService {

    private static final int TARGET_TOKENS = 512;
    private static final int OVERLAP_TOKENS = 64;
    private static final int MAX_TOKENS = 768;
    private static final int MIN_TOKENS = 50;
    private static final int APPROX_CHARS_PER_TOKEN = 4;
    private static final int TARGET_CHARS = TARGET_TOKENS * APPROX_CHARS_PER_TOKEN;
    private static final int OVERLAP_CHARS = OVERLAP_TOKENS * APPROX_CHARS_PER_TOKEN;
    private static final int MAX_CHARS = MAX_TOKENS * APPROX_CHARS_PER_TOKEN;
    private static final int MIN_CHARS = MIN_TOKENS * APPROX_CHARS_PER_TOKEN;

    @Override
    public ChunkingResult chunk(ParsedDocumentLayout parsedLayout) {
        List<ChunkCandidate> chunks = new ArrayList<>();
        ChunkAccumulator current = null;
        String currentHeading = null;
        int chunkIndex = 0;

        for (DocumentLayoutBlock block : parsedLayout.blocks()) {
            if (isHeading(block)) {
                if (current != null && !current.isEmpty()) {
                    chunkIndex = flushChunk(chunks, current, chunkIndex);
                    current = null;
                }
                currentHeading = normalizeHeading(block.content());
                continue;
            }

            if (block.blockType() == DocumentLayoutBlockType.TABLE) {
                if (current != null && !current.isEmpty()) {
                    chunkIndex = flushChunk(chunks, current, chunkIndex);
                    current = null;
                }
                chunks.add(buildSingleBlockChunk(chunkIndex++, block, currentHeading));
                continue;
            }

            if (!StringUtils.hasText(block.content())) {
                continue;
            }

            if (current == null) {
                current = new ChunkAccumulator(currentHeading);
            }

            String normalizedBlockContent = block.content().trim();
            if (current.lengthWith(normalizedBlockContent) <= TARGET_CHARS) {
                current.append(block);
                continue;
            }

            if (!current.isEmpty()) {
                chunkIndex = flushChunk(chunks, current, chunkIndex);
                current = createOverlapAccumulator(currentHeading, current);
            }

            if (normalizedBlockContent.length() > MAX_CHARS) {
                List<DocumentLayoutBlock> splitBlocks = splitLargeBlock(block, currentHeading);
                for (DocumentLayoutBlock splitBlock : splitBlocks) {
                    chunks.add(buildSingleBlockChunk(chunkIndex++, splitBlock, currentHeading));
                }
                current = null;
                continue;
            }

            if (current == null) {
                current = new ChunkAccumulator(currentHeading);
            }
            current.append(block);
        }

        if (current != null && !current.isEmpty()) {
            flushChunk(chunks, current, chunkIndex);
        }

        return mergeUndersizedTailChunk(new ChunkingResult(chunks));
    }

    private boolean isHeading(DocumentLayoutBlock block) {
        return block.blockType() == DocumentLayoutBlockType.TITLE
                || block.blockType() == DocumentLayoutBlockType.SECTION_HEADING;
    }

    private int flushChunk(List<ChunkCandidate> chunks, ChunkAccumulator accumulator, int chunkIndex) {
        chunks.add(accumulator.toChunk(chunkIndex));
        return chunkIndex + 1;
    }

    private ChunkAccumulator createOverlapAccumulator(String heading, ChunkAccumulator previous) {
        String overlap = previous.tail(OVERLAP_CHARS);
        if (!StringUtils.hasText(overlap)) {
            return new ChunkAccumulator(heading);
        }

        ChunkAccumulator accumulator = new ChunkAccumulator(heading);
        accumulator.appendRaw(overlap, previous.pageNumber(), previous.startOffset(), previous.endOffset());
        return accumulator;
    }

    private ChunkCandidate buildSingleBlockChunk(int chunkIndex, DocumentLayoutBlock block, String currentHeading) {
        String content = prependHeading(currentHeading, block.content());
        return new ChunkCandidate(
                chunkIndex,
                content,
                block.pageNumber(),
                currentHeading,
                block.startOffset(),
                block.endOffset());
    }

    private List<DocumentLayoutBlock> splitLargeBlock(DocumentLayoutBlock block, String currentHeading) {
        List<DocumentLayoutBlock> splitBlocks = new ArrayList<>();
        String content = block.content();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + MAX_CHARS, content.length());
            if (end < content.length()) {
                int lastBoundary = Math.max(
                        content.lastIndexOf(' ', end),
                        Math.max(content.lastIndexOf('.', end), content.lastIndexOf('\n', end)));
                if (lastBoundary > start + MIN_CHARS / 2) {
                    end = lastBoundary;
                }
            }

            String slice = content.substring(start, end).trim();
            if (StringUtils.hasText(slice)) {
                int startOffset = block.startOffset() == null ? start : block.startOffset() + start;
                int endOffset = block.startOffset() == null ? end : block.startOffset() + end;
                splitBlocks.add(new DocumentLayoutBlock(
                        splitBlocks.size(),
                        block.blockType(),
                        slice,
                        block.pageNumber(),
                        block.paragraphRole(),
                        startOffset,
                        endOffset,
                        block.tableRowCount(),
                        block.tableColumnCount()));
            }
            if (end >= content.length()) {
                break;
            }
            start = Math.max(end - OVERLAP_CHARS, start + 1);
        }

        return splitBlocks;
    }

    private ChunkingResult mergeUndersizedTailChunk(ChunkingResult result) {
        List<ChunkCandidate> chunks = new ArrayList<>(result.chunks());
        if (chunks.size() < 2) {
            return result;
        }

        ChunkCandidate last = chunks.getLast();
        if (last.content().length() >= MIN_CHARS) {
            return result;
        }

        ChunkCandidate previous = chunks.get(chunks.size() - 2);
        ChunkCandidate merged = new ChunkCandidate(
                previous.chunkIndex(),
                previous.content() + System.lineSeparator() + System.lineSeparator() + last.content(),
                previous.pageNumber() != null ? previous.pageNumber() : last.pageNumber(),
                previous.sectionHeading() != null ? previous.sectionHeading() : last.sectionHeading(),
                previous.startCharOffset(),
                last.endCharOffset());
        chunks.set(chunks.size() - 2, merged);
        chunks.removeLast();

        List<ChunkCandidate> reindexed = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            ChunkCandidate chunk = chunks.get(index);
            reindexed.add(new ChunkCandidate(
                    index,
                    chunk.content(),
                    chunk.pageNumber(),
                    chunk.sectionHeading(),
                    chunk.startCharOffset(),
                    chunk.endCharOffset()));
        }
        return new ChunkingResult(reindexed);
    }

    private String prependHeading(String heading, String content) {
        if (!StringUtils.hasText(heading)) {
            return content.trim();
        }
        return "[Section: %s] %s".formatted(heading.trim(), content.trim());
    }

    private String normalizeHeading(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static final class ChunkAccumulator {

        private final String heading;
        private final StringBuilder body = new StringBuilder();
        private Integer pageNumber;
        private Integer startOffset;
        private Integer endOffset;

        private ChunkAccumulator(String heading) {
            this.heading = heading;
        }

        private void append(DocumentLayoutBlock block) {
            appendRaw(block.content(), block.pageNumber(), block.startOffset(), block.endOffset());
        }

        private void appendRaw(String text, Integer pageNumber, Integer startOffset, Integer endOffset) {
            if (!StringUtils.hasText(text)) {
                return;
            }
            if (!body.isEmpty()) {
                body.append(System.lineSeparator()).append(System.lineSeparator());
            }
            body.append(text.trim());
            if (this.pageNumber == null) {
                this.pageNumber = pageNumber;
            }
            if (this.startOffset == null) {
                this.startOffset = startOffset;
            }
            this.endOffset = endOffset != null ? endOffset : this.endOffset;
        }

        private boolean isEmpty() {
            return body.isEmpty();
        }

        private int lengthWith(String text) {
            int delimiterLength = body.isEmpty() ? 0 : 4;
            return body.length() + delimiterLength + text.length() + headingPrefixLength();
        }

        private ChunkCandidate toChunk(int chunkIndex) {
            return new ChunkCandidate(
                    chunkIndex,
                    headingPrefix() + body,
                    pageNumber,
                    heading,
                    startOffset,
                    endOffset);
        }

        private String tail(int maxChars) {
            if (body.isEmpty()) {
                return "";
            }
            String text = body.toString();
            if (text.length() <= maxChars) {
                return text;
            }
            int start = Math.max(0, text.length() - maxChars);
            int boundary = text.indexOf(' ', start);
            if (boundary > start && boundary < text.length()) {
                start = boundary + 1;
            }
            return text.substring(start).trim();
        }

        private Integer pageNumber() {
            return pageNumber;
        }

        private Integer startOffset() {
            return startOffset;
        }

        private Integer endOffset() {
            return endOffset;
        }

        private int headingPrefixLength() {
            return headingPrefix().length();
        }

        private String headingPrefix() {
            return StringUtils.hasText(heading)
                    ? "[Section: %s] ".formatted(heading.trim())
                    : "";
        }
    }
}
