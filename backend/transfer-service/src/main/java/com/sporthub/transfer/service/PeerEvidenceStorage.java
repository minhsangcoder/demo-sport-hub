package com.sporthub.transfer.service;

import com.sporthub.common.exception.ActionPolicyException;
import io.minio.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** A dedicated non-public bucket; browsers only receive bytes through the scoped SportHub proxy. */
@Component
public class PeerEvidenceStorage {
  private final MinioClient storage;
  private final String bucket;

  public PeerEvidenceStorage(
      @Value("${MINIO_ENDPOINT:http://localhost:9000}") String endpoint,
      @Value("${MINIO_ACCESS_KEY:}") String access,
      @Value("${MINIO_SECRET_KEY:}") String secret,
      @Value("${MINIO_PEER_EVIDENCE_BUCKET:sporthub-peer-evidence}") String bucket) {
    if (!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))
      throw new IllegalStateException("Tên kho chứng cứ riêng tư không hợp lệ.");
    this.bucket = bucket;
    storage =
        access.isBlank() || secret.isBlank()
            ? null
            : MinioClient.builder()
                .endpoint(endpoint)
                .credentials(access, secret)
                .httpClient(
                    new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .writeTimeout(5, TimeUnit.SECONDS)
                        .callTimeout(8, TimeUnit.SECONDS)
                        .build())
                .build();
  }

  public void put(String key, String type, byte[] bytes) {
    try {
      requirePrivate();
      storage.putObject(
          PutObjectArgs.builder().bucket(bucket).object(key).contentType(type).stream(
                  new ByteArrayInputStream(bytes), bytes.length, -1)
              .build());
    } catch (Exception ex) {
      throw unavailable();
    }
  }

  public byte[] get(String key) {
    try {
      requirePrivate();
      try (var input =
          storage.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
        byte[] bytes = input.readNBytes(5 * 1024 * 1024 + 1);
        if (bytes.length > 5 * 1024 * 1024) throw new IOException();
        return bytes;
      }
    } catch (Exception ex) {
      throw unavailable();
    }
  }

  public void remove(String key) {
    try {
      requirePrivate();
      storage.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
    } catch (Exception ex) {
      throw unavailable();
    }
  }

  private void requirePrivate() throws Exception {
    if (storage == null) throw unavailable();
    if (!storage.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
      try {
        storage.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      } catch (io.minio.errors.ErrorResponseException ex) {
        if (!"BucketAlreadyOwnedByYou".equals(ex.errorResponse().code())) throw ex;
      }
    }
    try {
      String policy = storage.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(bucket).build());
      if (policy != null && !policy.isBlank()) throw unavailable();
    } catch (io.minio.errors.ErrorResponseException ex) {
      if (!"NoSuchBucketPolicy".equals(ex.errorResponse().code())) throw ex;
    }
  }

  static ActionPolicyException unavailable() {
    return new ActionPolicyException(
        503,
        "PEER_EVIDENCE_STORAGE_UNAVAILABLE",
        "Kho chứng cứ riêng tư chưa sẵn sàng. Vui lòng thử lại cùng yêu cầu.",
        Map.of());
  }
}
