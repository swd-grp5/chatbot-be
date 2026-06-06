package swdchatbox.system.document.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.document.entity.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class DocumentStorageService {

    private final DocumentStorageProperties properties;
    private final Path basePath;
    private final S3Client s3Client;

    public DocumentStorageService(
            DocumentStorageProperties properties,
            @Autowired(required = false) S3Client s3Client
    ) {
        this.properties = properties;
        this.basePath = Path.of(properties.getBasePath()).toAbsolutePath().normalize();
        this.s3Client = s3Client;
    }

    @PostConstruct
    void initStorage() throws IOException {
        if (isS3Enabled()) {
            log.info("Document storage: AWS S3 bucket={} region={} prefix={}",
                    properties.getS3().getBucket(),
                    properties.getS3().getRegion(),
                    properties.getS3().getKeyPrefix());
            return;
        }
        Files.createDirectories(basePath);
        log.info("Document storage: local path={}", basePath);
    }

    public boolean isS3Enabled() {
        DocumentStorageProperties.S3 s3 = properties.getS3();
        return s3 != null
                && s3.getBucket() != null
                && !s3.getBucket().isBlank()
                && s3Client != null;
    }

    public String computeChecksum(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(properties.getChecksumAlgorithm());
            return HexFormat.of().formatHex(digest.digest(file.getBytes()));
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new BadRequestException("Failed to compute file checksum");
        }
    }

    public StoredDocumentFile store(UUID documentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }

        try {
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getName();
            String storedFileName = UUID.randomUUID() + "_" + originalName;
            byte[] content = file.getBytes();
            MessageDigest digest = MessageDigest.getInstance(properties.getChecksumAlgorithm());
            String checksum = HexFormat.of().formatHex(digest.digest(content));

            if (isS3Enabled()) {
                String objectKey = buildObjectKey(documentId, storedFileName);
                uploadToS3(objectKey, content, file.getContentType());
                return new StoredDocumentFile(originalName, storedFileName, objectKey, checksum);
            }

            Files.createDirectories(basePath.resolve(documentId.toString()));
            Path target = basePath.resolve(documentId.toString()).resolve(storedFileName).normalize();
            if (!target.startsWith(basePath)) {
                throw new BadRequestException("Invalid storage path");
            }
            Files.write(target, content);
            return new StoredDocumentFile(originalName, storedFileName, target.toString(), checksum);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BadRequestException("Failed to store uploaded file");
        }
    }

    public byte[] readFile(UUID documentId, DocumentFile file) {
        try (InputStream inputStream = openInputStream(documentId, file)) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new BadRequestException("Failed to read stored file");
        }
    }

    public InputStream openInputStream(UUID documentId, DocumentFile file) {
        if (file == null) {
            throw new ResourceNotFoundException("File not found");
        }
        if (isStoredOnS3(file)) {
            return downloadFromS3(file.getFilePath());
        }
        Path path = resolveLocalPath(documentId, file);
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("File not found on disk");
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new BadRequestException("Failed to read stored file");
        }
    }

    public ReadableStoredFile openReadableFile(UUID documentId, DocumentFile file) {
        if (file == null) {
            throw new ResourceNotFoundException("File not found");
        }
        if (isStoredOnS3(file)) {
            var response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .key(file.getFilePath())
                    .build());
            long fileSize = response.response().contentLength() != null
                    ? response.response().contentLength()
                    : file.getFileSize() != null ? file.getFileSize() : 0L;
            return new ReadableStoredFile(new InputStreamResource(response), fileSize);
        }

        Path path = resolveLocalPath(documentId, file);
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("File not found on disk");
        }
        try {
            return new ReadableStoredFile(new FileSystemResource(path), Files.size(path));
        } catch (IOException e) {
            throw new BadRequestException("Failed to read stored file");
        }
    }

    public long getStoredFileSize(UUID documentId, DocumentFile file) {
        if (file == null) {
            throw new ResourceNotFoundException("File not found");
        }
        if (isStoredOnS3(file)) {
            try {
                var response = s3Client.headObject(HeadObjectRequest.builder()
                        .bucket(properties.getS3().getBucket())
                        .key(file.getFilePath())
                        .build());
                return response.contentLength();
            } catch (NoSuchKeyException e) {
                throw new ResourceNotFoundException("File not found on S3");
            }
        }
        Path path = resolveLocalPath(documentId, file);
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("File not found on disk");
        }
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new BadRequestException("Failed to read stored file");
        }
    }

    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        if (isS3Enabled() && isS3ObjectKey(filePath)) {
            deleteFromS3(filePath);
            return;
        }
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException e) {
            throw new BadRequestException("Failed to delete stored file");
        }
    }

    public void deleteDocumentFolder(UUID documentId) {
        if (isS3Enabled()) {
            deleteS3Prefix(buildDocumentPrefix(documentId));
            return;
        }

        Path folder = basePath.resolve(documentId.toString());
        if (!Files.exists(folder)) {
            return;
        }
        try (var paths = Files.walk(folder)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new BadRequestException("Failed to delete document files");
        } catch (RuntimeException e) {
            throw new BadRequestException("Failed to delete document files");
        }
    }

    private void uploadToS3(String objectKey, byte[] content, String contentType) {
        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .key(objectKey);
            if (contentType != null && !contentType.isBlank()) {
                requestBuilder.contentType(contentType);
            }
            s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            throw new BadRequestException("Failed to store uploaded file to S3");
        }
    }

    private InputStream downloadFromS3(String objectKey) {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .key(objectKey)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException("File not found on S3");
        }
    }

    private void deleteFromS3(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            throw new BadRequestException("Failed to delete stored file from S3");
        }
    }

    private void deleteS3Prefix(String prefix) {
        String continuationToken = null;
        do {
            var listResponse = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(properties.getS3().getBucket())
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build());
            for (S3Object object : listResponse.contents()) {
                deleteFromS3(object.key());
            }
            continuationToken = listResponse.isTruncated() ? listResponse.nextContinuationToken() : null;
        } while (continuationToken != null);
    }

    private boolean isStoredOnS3(DocumentFile file) {
        return isS3Enabled() && isS3ObjectKey(file.getFilePath());
    }

    private boolean isS3ObjectKey(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        String prefix = properties.getS3().getKeyPrefix();
        return filePath.startsWith(prefix + "/");
    }

    private String buildObjectKey(UUID documentId, String storedFileName) {
        return buildDocumentPrefix(documentId) + storedFileName;
    }

    private String buildDocumentPrefix(UUID documentId) {
        return properties.getS3().getKeyPrefix() + "/" + documentId + "/";
    }

    private Path resolveLocalPath(UUID documentId, DocumentFile file) {
        Path fromBasePath = basePath.resolve(documentId.toString()).resolve(file.getStoredFileName()).normalize();
        if (Files.exists(fromBasePath)) {
            return fromBasePath;
        }

        if (file.getFilePath() != null && !file.getFilePath().isBlank()) {
            Path fromDatabase = Path.of(file.getFilePath()).toAbsolutePath().normalize();
            if (Files.exists(fromDatabase)) {
                return fromDatabase;
            }
        }

        return fromBasePath;
    }

    public record StoredDocumentFile(String originalFileName, String storedFileName, String filePath, String checksum) {}

    public record ReadableStoredFile(Resource resource, long fileSize) {}
}
