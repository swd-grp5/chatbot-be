package swdchatbox.system.ingestion.enums;

public enum IngestionJobStatus {
    PENDING,
    EXTRACTING_TEXT,
    CHUNKING,
    EMBEDDING,
    COMPLETED,
    FAILED
}