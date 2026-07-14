package uz.hrlab.grading.integration.idp.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.IdentityProvisioningToggle;

/**
 * Configuration for the ZITADEL identity-provisioning adapter —
 * {@code grading.idp.zitadel.*}.
 *
 * <p>FEATURE-FLAGGED: {@link #enabled} defaults to {@code false} so local, test
 * and CI environments inject the {@code NoOpIdentityProvisioningClient} and the
 * invite flow stays DB-only (no IdP, no network). Production sets the values via
 * env vars relaxed-bound by Spring:
 * <pre>
 *   GRADING_IDP_ZITADEL_ENABLED   -> enabled
 *   GRADING_IDP_ZITADEL_API_BASE  -> apiBase
 *   GRADING_IDP_ZITADEL_ORG_ID    -> orgId
 *   GRADING_IDP_ZITADEL_TOKEN     -> token   (machine-user PAT)
 * </pre>
 *
 * <p>SECRET HANDLING: {@link #token} is a high-value platform secret (it can
 * mint identities). It is sourced only from the environment, NEVER committed,
 * and NEVER logged. {@link #toString()} is overridden so an accidental log of
 * this object cannot leak it.
 */
@Component
@ConfigurationProperties(prefix = "grading.idp.zitadel")
public class ZitadelIdpProperties implements IdentityProvisioningToggle {

    /** Master switch. When false the provisioning port is a NO-OP. */
    private boolean enabled = false;

    /** ZITADEL base URL, e.g. {@code https://auth.hrlab.uz}. */
    private String apiBase;

    /** ZITADEL organization id the human users are created under. */
    private String orgId;

    /** Machine-user PAT (Bearer). Sensitive — never logged. */
    private String token;

    /** Connect timeout in milliseconds for the IdP HTTP client. */
    private int connectTimeoutMs = 5_000;

    /** Read timeout in milliseconds for the IdP HTTP client. */
    private int readTimeoutMs = 10_000;

    @Override
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getApiBase() { return apiBase; }
    public void setApiBase(String apiBase) { this.apiBase = apiBase; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    /**
     * Redacted — NEVER include {@link #token} so this object cannot leak the
     * PAT if it is ever logged or appears in a stack trace.
     */
    @Override
    public String toString() {
        return "ZitadelIdpProperties{enabled=" + enabled
                + ", apiBase=" + apiBase
                + ", orgId=" + orgId
                + ", token=***REDACTED***"
                + ", connectTimeoutMs=" + connectTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs + "}";
    }
}
