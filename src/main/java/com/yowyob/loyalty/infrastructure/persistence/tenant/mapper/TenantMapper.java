package com.yowyob.loyalty.infrastructure.persistence.tenant.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.loyalty.domain.shared.model.AuditInfo;
import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.domain.tenant.model.Tenant;
import com.yowyob.loyalty.domain.tenant.model.TenantConfig;
import com.yowyob.loyalty.domain.tenant.model.enums.TenantPlan;
import com.yowyob.loyalty.domain.tenant.model.enums.TenantStatus;
import com.yowyob.loyalty.infrastructure.persistence.tenant.entity.TenantEntity;
import com.yowyob.loyalty.infrastructure.security.crypto.SecretCipher;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class TenantMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected SecretCipher secretCipher;

    @Mapping(target = "id", expression = "java(mapId(entity.getId()))")
    @Mapping(target = "status", expression = "java(mapStatus(entity.getStatus()))")
    @Mapping(target = "plan", expression = "java(mapPlan(entity.getPlan()))")
    @Mapping(target = "config", expression = "java(parseConfig(entity.getConfig()))")
    @Mapping(target = "auditInfo", expression = "java(mapAudit(entity))")
    public abstract Tenant toDomain(TenantEntity entity);

    @Mapping(target = "id", expression = "java(domain.getId().value())")
    @Mapping(target = "status", expression = "java(domain.getStatus().name())")
    @Mapping(target = "plan", expression = "java(domain.getPlan().name())")
    @Mapping(target = "config", expression = "java(serializeConfig(domain.getConfig()))")
    @Mapping(target = "createdAt", source = "auditInfo.createdAt")
    @Mapping(target = "updatedAt", source = "auditInfo.updatedAt")
    @Mapping(target = "createdBy", source = "auditInfo.createdBy")
    @Mapping(target = "updatedBy", source = "auditInfo.updatedBy")
    public abstract TenantEntity toEntity(Tenant domain);

    protected TenantId mapId(UUID id) {
        return id != null ? TenantId.of(id) : null;
    }

    protected TenantStatus mapStatus(String status) {
        return status != null ? TenantStatus.valueOf(status) : null;
    }

    protected TenantPlan mapPlan(String plan) {
        return plan != null ? TenantPlan.valueOf(plan) : null;
    }

    protected AuditInfo mapAudit(TenantEntity entity) {
        if (entity.getCreatedAt() == null) return null;
        return new AuditInfo(entity.getCreatedAt(), entity.getUpdatedAt(), entity.getCreatedBy(), entity.getUpdatedBy());
    }

    /**
     * Le mot de passe Bonification est le seul secret de TenantConfig : il est chiffré dans le
     * JSONB et rendu en clair au domaine, qui n'a pas à connaître le stockage. Une valeur non
     * chiffrée est une valeur historique, relue telle quelle puis chiffrée à la prochaine
     * écriture — voir SecretCipher.
     */
    protected TenantConfig parseConfig(String json) {
        if (json == null || json.isBlank()) return TenantConfig.defaults();
        try {
            TenantConfig stored = objectMapper.readValue(json, TenantConfig.class);
            return withBonificationPassword(stored, secretCipher.decrypt(stored.bonificationApiPassword()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erreur de désérialisation de TenantConfig", e);
        }
    }

    protected String serializeConfig(TenantConfig config) {
        if (config == null) return "{}";
        try {
            TenantConfig toStore =
                    withBonificationPassword(config, secretCipher.encrypt(config.bonificationApiPassword()));
            return objectMapper.writeValueAsString(toStore);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erreur de sérialisation de TenantConfig", e);
        }
    }

    private static TenantConfig withBonificationPassword(TenantConfig config, String password) {
        return new TenantConfig(
                config.defaultCurrencyCode(),
                config.walletAutoActivate(),
                config.pointExpiryDays(),
                config.notificationChannels(),
                config.bonificationApiUsername(),
                password);
    }
}
