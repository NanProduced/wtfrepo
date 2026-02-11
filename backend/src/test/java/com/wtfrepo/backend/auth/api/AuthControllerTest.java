package com.wtfrepo.backend.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.wtfrepo.backend.BackendApplication;
import com.wtfrepo.backend.auth.application.support.AuthConstants;
import com.wtfrepo.backend.auth.domain.OAuthProvider;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(
    properties = {
      "app.auth.store=jpa",
      "app.auth.identity-proof.issuer=wtf-repo-bff-test",
      "app.auth.identity-proof.audience=wtf-repo-backend-auth-exchange-test",
      "app.auth.identity-proof.secret=test-identity-proof-secret-0123456789abcdef",
      "app.auth.identity-proof.clock-skew=30s",
      "app.auth.identity-proof.max-ttl=5m",
      "app.auth.economy.initial-bug-grant=5",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
    })
class AuthControllerTest {

  private static final String TEST_IDENTITY_PROOF_ISSUER = "wtf-repo-bff-test";
  private static final String TEST_IDENTITY_PROOF_AUDIENCE = "wtf-repo-backend-auth-exchange-test";
  private static final String TEST_IDENTITY_PROOF_SECRET =
      "test-identity-proof-secret-0123456789abcdef";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void exchangeShouldIssueBackendToken() throws Exception {
    String proof = buildIdentityProof(OAuthProvider.GITHUB, "gh-sub-1", "jti-ex-1");
    AuthExchangeRequest request =
        new AuthExchangeRequest(
            com.wtfrepo.backend.auth.domain.OAuthProvider.GITHUB,
            proof,
            "state-1",
            new AuthExchangeRequest.Profile("nan", "https://avatar"));

    mockMvc
        .perform(
            post("/api/v1/auth/exchange")
                .header(RequestIdConstants.HEADER_NAME, "req-ex-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(header().string(RequestIdConstants.HEADER_NAME, "req-ex-1"))
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.user.userId").isNotEmpty())
        .andExpect(jsonPath("$.user.roles[0]").value("USER"));
  }

  @Test
  void exchangeShouldBeIdempotentByExplicitIdempotencyKey() throws Exception {
    String proof = buildIdentityProof(OAuthProvider.GITHUB, "gh-sub-idem-header", "jti-idem-header");
    AuthExchangeRequest request =
        new AuthExchangeRequest(
            OAuthProvider.GITHUB,
            proof,
            "state-idem-header",
            new AuthExchangeRequest.Profile("nan", "https://avatar"));

    String firstBody =
        mockMvc
            .perform(
                post("/api/v1/auth/exchange")
                    .header(RequestIdConstants.HEADER_NAME, "req-idem-header-1")
                    .header(AuthConstants.Header.IDEMPOTENCY_KEY, "idem-exchange-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String secondBody =
        mockMvc
            .perform(
                post("/api/v1/auth/exchange")
                    .header(RequestIdConstants.HEADER_NAME, "req-idem-header-2")
                    .header(AuthConstants.Header.IDEMPOTENCY_KEY, "idem-exchange-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String firstToken = objectMapper.readTree(firstBody).path("accessToken").asText();
    String secondToken = objectMapper.readTree(secondBody).path("accessToken").asText();
    org.assertj.core.api.Assertions.assertThat(secondToken).isEqualTo(firstToken);
  }

  @Test
  void meShouldRequireAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/me").header(RequestIdConstants.HEADER_NAME, "req-me-unauth"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.requestId").value("req-me-unauth"));
  }

  @Test
  void meAndRenameShouldWorkWithIssuedToken() throws Exception {
    String token =
        issueToken(
            buildIdentityProof(OAuthProvider.GITHUB, "gh-sub-2", "jti-ex-2"),
            "state-2",
            "req-ex-2");

    mockMvc
        .perform(
            get("/api/v1/me")
                .header(RequestIdConstants.HEADER_NAME, "req-me-1")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").isNotEmpty())
        .andExpect(jsonPath("$.username").isNotEmpty());

    RenameUsernameRequest renameRequest = new RenameUsernameRequest("patient_newname");

    mockMvc
        .perform(
            patch("/api/v1/me/username")
                .header(RequestIdConstants.HEADER_NAME, "req-rn-1")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(renameRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("patient_newname"))
        .andExpect(jsonPath("$.usernameChanged").value(true));
  }

  @Test
  void exchangeShouldBeIdempotentByRequestId() throws Exception {
    String proof = buildIdentityProof(OAuthProvider.GITHUB, "gh-sub-idempotent", "jti-idempotent");
    AuthExchangeRequest request =
        new AuthExchangeRequest(
            com.wtfrepo.backend.auth.domain.OAuthProvider.GITHUB,
            proof,
            "state-idempotent",
            new AuthExchangeRequest.Profile("nan", "https://avatar"));

    String firstBody =
        mockMvc
            .perform(
                post("/api/v1/auth/exchange")
                    .header(RequestIdConstants.HEADER_NAME, "req-idem-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String secondBody =
        mockMvc
            .perform(
                post("/api/v1/auth/exchange")
                    .header(RequestIdConstants.HEADER_NAME, "req-idem-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String firstToken = objectMapper.readTree(firstBody).path("accessToken").asText();
    String secondToken = objectMapper.readTree(secondBody).path("accessToken").asText();
    org.assertj.core.api.Assertions.assertThat(secondToken).isEqualTo(firstToken);
  }

  @Test
  void exchangeShouldRejectConsumedOAuthStateWithDifferentRequestId() throws Exception {
    String proof = buildIdentityProof(OAuthProvider.GITHUB, "gh-sub-state", "jti-state");
    AuthExchangeRequest request =
        new AuthExchangeRequest(
            com.wtfrepo.backend.auth.domain.OAuthProvider.GITHUB,
            proof,
            "state-consumed",
            new AuthExchangeRequest.Profile("nan", "https://avatar"));

    mockMvc
        .perform(
            post("/api/v1/auth/exchange")
                .header(RequestIdConstants.HEADER_NAME, "req-state-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/exchange")
                .header(RequestIdConstants.HEADER_NAME, "req-state-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void exchangeShouldRejectIdempotencyConflictForSameRequestId() throws Exception {
    String firstProof =
        buildIdentityProof(OAuthProvider.GITHUB, "gh-sub-conflict-1", "jti-conflict-1");
    String secondProof =
        buildIdentityProof(OAuthProvider.GITHUB, "gh-sub-conflict-2", "jti-conflict-2");
    AuthExchangeRequest first =
        new AuthExchangeRequest(
            com.wtfrepo.backend.auth.domain.OAuthProvider.GITHUB,
            firstProof,
            "state-conflict-1",
            new AuthExchangeRequest.Profile("nan", "https://avatar"));

    AuthExchangeRequest second =
        new AuthExchangeRequest(
            com.wtfrepo.backend.auth.domain.OAuthProvider.GITHUB,
            secondProof,
            "state-conflict-2",
            new AuthExchangeRequest.Profile("nan", "https://avatar"));

    mockMvc
        .perform(
            post("/api/v1/auth/exchange")
                .header(RequestIdConstants.HEADER_NAME, "req-idem-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/exchange")
                .header(RequestIdConstants.HEADER_NAME, "req-idem-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(second)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void exchangeShouldRejectIdempotencyConflictForSameIdempotencyKey() throws Exception {
    String firstProof =
        buildIdentityProof(OAuthProvider.GITHUB, "gh-sub-header-conflict-1", "jti-header-conflict-1");
    String secondProof =
        buildIdentityProof(OAuthProvider.GITHUB, "gh-sub-header-conflict-2", "jti-header-conflict-2");
    AuthExchangeRequest first =
        new AuthExchangeRequest(
            OAuthProvider.GITHUB,
            firstProof,
            "state-header-conflict-1",
            new AuthExchangeRequest.Profile("nan", "https://avatar"));

    AuthExchangeRequest second =
        new AuthExchangeRequest(
            OAuthProvider.GITHUB,
            secondProof,
            "state-header-conflict-2",
            new AuthExchangeRequest.Profile("nan", "https://avatar"));

    mockMvc
        .perform(
            post("/api/v1/auth/exchange")
                .header(RequestIdConstants.HEADER_NAME, "req-header-conflict-1")
                .header(AuthConstants.Header.IDEMPOTENCY_KEY, "idem-conflict-header")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/exchange")
                .header(RequestIdConstants.HEADER_NAME, "req-header-conflict-2")
                .header(AuthConstants.Header.IDEMPOTENCY_KEY, "idem-conflict-header")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(second)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void renameShouldRejectForbiddenKeyword() throws Exception {
    String token =
        issueToken(
            buildIdentityProof(
                OAuthProvider.GITHUB,
                "gh-sub-rename-forbidden",
                "jti-rename-forbidden"),
            "state-rename-forbidden",
            "req-ex-rf-1");

    RenameUsernameRequest renameRequest = new RenameUsernameRequest("admin_user");

    mockMvc
        .perform(
            patch("/api/v1/me/username")
                .header(RequestIdConstants.HEADER_NAME, "req-rn-forbidden")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(renameRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void exchangeShouldRejectProviderMismatchInIdentityProof() throws Exception {
    String proof = buildIdentityProof(OAuthProvider.GOOGLE, "go-sub-1", "jti-provider-mismatch");
    AuthExchangeRequest request =
        new AuthExchangeRequest(
            com.wtfrepo.backend.auth.domain.OAuthProvider.GITHUB,
            proof,
            "state-prefix",
            new AuthExchangeRequest.Profile("nan", "https://avatar"));

    mockMvc
        .perform(
            post("/api/v1/auth/exchange")
                .header(RequestIdConstants.HEADER_NAME, "req-prefix")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void exchangeShouldRejectExpiredIdentityProof() throws Exception {
    Instant now = Instant.now();
    String expiredProof =
        buildIdentityProof(
            OAuthProvider.GITHUB,
            "gh-sub-expired",
            "jti-expired",
            now.minusSeconds(300),
            now.minusSeconds(120));

    AuthExchangeRequest request =
        new AuthExchangeRequest(
            OAuthProvider.GITHUB,
            expiredProof,
            "state-expired",
            new AuthExchangeRequest.Profile("nan", "https://avatar"));

    mockMvc
        .perform(
            post("/api/v1/auth/exchange")
                .header(RequestIdConstants.HEADER_NAME, "req-expired")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  private String issueToken(String proof, String state, String requestId) throws Exception {
    AuthExchangeRequest request =
        new AuthExchangeRequest(
            com.wtfrepo.backend.auth.domain.OAuthProvider.GITHUB,
            proof,
            state,
            new AuthExchangeRequest.Profile("nan", "https://avatar"));

    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/exchange")
                    .header(RequestIdConstants.HEADER_NAME, requestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readTree(body).path("accessToken").asText();
  }

  private String buildIdentityProof(OAuthProvider provider, String subject, String jti)
      throws JOSEException {
    Instant now = Instant.now();
    return buildIdentityProof(provider, subject, jti, now.minusSeconds(5), now.plusSeconds(120));
  }

  private String buildIdentityProof(
      OAuthProvider provider, String subject, String jti, Instant issuedAt, Instant expiresAt)
      throws JOSEException {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(TEST_IDENTITY_PROOF_ISSUER)
            .audience(TEST_IDENTITY_PROOF_AUDIENCE)
            .subject(subject)
            .claim("provider", provider.name())
            .jwtID(jti)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .build();

    SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    signedJwt.sign(new MACSigner(TEST_IDENTITY_PROOF_SECRET));
    return signedJwt.serialize();
  }
}
