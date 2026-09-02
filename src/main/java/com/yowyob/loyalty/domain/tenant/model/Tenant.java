package com.yowyob.loyalty.domain.tenant.model;

import com.yowyob.loyalty.domain.shared.exception.DomainValidationException;
import com.yowyob.loyalty.domain.shared.model.AuditInfo;
import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.domain.tenant.model.enums.TenantPlan;
import com.yowyob.loyalty.domain.tenant.model.enums.TenantStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Tenant {
    private final TenantId id;
    private final String name;
    private final String slug;
    private final TenantStatus status;
    private final TenantPlan plan;
    private final TenantConfig config;
    private final AuditInfo auditInfo;

    @JsonCreator
    public Tenant(
            @JsonProperty("id") TenantId id,
            @JsonProperty("name") String name,
            @JsonProperty("slug") String slug,
            @JsonProperty("status") TenantStatus status,
            @JsonProperty("plan") TenantPlan plan,
            @JsonProperty("config") TenantConfig config,
            @JsonProperty("auditInfo") AuditInfo auditInfo) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.status = status;
        this.plan = plan;
        this.config = config;
        this.auditInfo = auditInfo;
    }

    public static Tenant create(TenantId id, String name, String slug, TenantPlan plan, TenantConfig config, String createdBy) {
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("name ne doit pas être vide");
        }
        if (slug == null || slug.isBlank()) {
            throw new DomainValidationException("slug ne doit pas être vide");
        }
        return new Tenant(
            id,
            name,
            slug,
            TenantStatus.PENDING_SETUP,
            plan,
            config,
            AuditInfo.now(createdBy)
        );
    }

    public Tenant withConfig(TenantConfig config) {
        return new Tenant(this.id, this.name, this.slug, this.status, this.plan, config, this.auditInfo);
    }

    public Tenant activate() {
        return new Tenant(
            this.id,
            this.name,
            this.slug,
            TenantStatus.ACTIVE,
            this.plan,
            this.config,
            this.auditInfo
        );
    }

    public Tenant suspend() {
        return new Tenant(
            this.id,
            this.name,
            this.slug,
            TenantStatus.SUSPENDED,
            this.plan,
            this.config,
            this.auditInfo
        );
    }

    /**
     * @JsonIgnore : sans ça, Jackson détecte ces prédicats comme des propriétés bean ("active",
     * "suspended") et les sérialise en plus des 7 champs du constructeur @JsonCreator — la
     * relecture depuis Redis échoue alors avec UnrecognizedPropertyException (vérifié en
     * conditions réelles, voir TenantCacheAdapter).
     */
    @JsonIgnore
    public boolean isActive() {
        return this.status == TenantStatus.ACTIVE;
    }

    @JsonIgnore
    public boolean isSuspended() {
        return this.status == TenantStatus.SUSPENDED;
    }

    public boolean canCreateMoreRules(int currentRuleCount) {
        return currentRuleCount < this.plan.getMaxRules();
    }

    public TenantId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public TenantPlan getPlan() {
        return plan;
    }

    public TenantConfig getConfig() {
        return config;
    }

    public AuditInfo getAuditInfo() {
        return auditInfo;
    }
}
