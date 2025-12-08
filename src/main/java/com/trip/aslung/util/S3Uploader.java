package com.trip.aslung.util;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class S3Uploader {

    private final S3Template s3Template;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    public String upload(MultipartFile multipartFile, String dirName) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return null;
        }

        // 1. 파일 이름 중복 방지를 위해 UUID 생성
        String originalFileName = multipartFile.getOriginalFilename();
        String uuid = UUID.randomUUID().toString();
        String fileName = dirName + "/" + uuid + "_" + originalFileName;

        try {
            // 2. S3에 업로드
            S3Resource resource = s3Template.upload(
                    bucket,
                    fileName,
                    multipartFile.getInputStream(),
                    ObjectMetadata.builder().contentType(multipartFile.getContentType()).build()
            );

            // 3. 업로드된 파일의 접근 URL 반환
            return resource.getURL().toString();

        } catch (IOException e) {
            log.error("S3 파일 업로드 중 오류 발생", e);
            throw new RuntimeException("파일 업로드 실패");
        }
    }
}