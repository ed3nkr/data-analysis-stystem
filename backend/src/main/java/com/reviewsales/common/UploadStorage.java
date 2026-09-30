package com.reviewsales.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.reviewsales.config.AppProperties;

/** 업로드 원본 파일을 ./data/uploads/{storeId}/{jobId}_{파일명} 으로 보관한다. */
@Component
public class UploadStorage {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private final Path root;

    public UploadStorage(AppProperties props) {
        this.root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
    }

    public String save(Long storeId, Long jobId, String originalName, byte[] bytes) {
        try {
            Path dir = root.resolve(String.valueOf(storeId));
            Files.createDirectories(dir);
            Path file = dir.resolve(jobId + "_" + sanitize(originalName));
            Files.write(file, bytes);
            return file.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 저장 경로에서 원래 파일명을 복원한다. */
    public static String originalName(String filePath) {
        if (filePath == null) {
            return null;
        }
        String name = Path.of(filePath).getFileName().toString();
        int idx = name.indexOf('_');
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    public static void checkSize(long size) {
        if (size > MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    private static String sanitize(String name) {
        String base = name == null || name.isBlank() ? "upload.csv" : Path.of(name).getFileName().toString();
        return base.replaceAll("[^0-9A-Za-z가-힣._-]", "_");
    }
}
