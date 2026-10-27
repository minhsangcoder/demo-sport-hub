package com.sporthub.transfer;

import static org.assertj.core.api.Assertions.*;

import com.sporthub.common.exception.ActionPolicyException;
import com.sporthub.transfer.service.PeerEvidenceStorage;
import io.minio.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class PeerEvidenceStorageTest {
  @Test
  void dedicatedBucketIsNotAnonymousAndAChangedPublicPolicyFailsClosed() throws Exception {
    String image =
        "ghcr.io/coollabsio/minio:RELEASE.2025-04-22T22-12-26Z@sha256:a4938f37f1be1841b8e7b627ad0207b265345fd0d063e42d7410c78af0e63e68";
    try (var minio =
        new GenericContainer<>(DockerImageName.parse(image))
            .withEnv("MINIO_ROOT_USER", "synthetic-user")
            .withEnv("MINIO_ROOT_PASSWORD", "synthetic-only-password")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000))) {
      minio.start();
      String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
      String bucket = "sporthub-private-test";
      var storage =
          new PeerEvidenceStorage(endpoint, "synthetic-user", "synthetic-only-password", bucket);
      String key = "peer/" + UUID.randomUUID() + "/" + UUID.randomUUID();
      storage.put(key, "image/png", new byte[] {1, 2, 3});
      assertThat(storage.get(key)).containsExactly(1, 2, 3);
      var anonymous =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(URI.create(endpoint + "/" + bucket + "/" + key))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.discarding());
      assertThat(anonymous.statusCode()).isEqualTo(403);
      var admin =
          MinioClient.builder()
              .endpoint(endpoint)
              .credentials("synthetic-user", "synthetic-only-password")
              .build();
      String policy =
          "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::"
              + bucket
              + "/*\"]}]}";
      admin.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
      assertThatThrownBy(() -> storage.get(key))
          .isInstanceOf(ActionPolicyException.class)
          .hasNoCause();
      assertThatThrownBy(() -> storage.put(key, "image/png", new byte[] {4}))
          .isInstanceOf(ActionPolicyException.class);
      admin.deleteBucketPolicy(DeleteBucketPolicyArgs.builder().bucket(bucket).build());
      assertThat(storage.get(key)).containsExactly(1, 2, 3);
      storage.remove(key);
      assertThatThrownBy(() -> storage.get(key)).isInstanceOf(ActionPolicyException.class);
    }
  }
}
