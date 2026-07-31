package com.yowyob.loyalty.infrastructure.kernelcore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kernel Core PaymentOrderResponse (/api/payments/orders).
 *
 * <p>{@code status} est typé {@code string} libre dans l'OpenAPI de Kernel Core : aucune
 * énumération n'est publiée, d'où l'interprétation défensive dans
 * {@code KernelPaymentStatusMapper}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KernelPaymentOrderDto {

    private String id;
    private String tenantId;
    private String clientId;
    private String serviceCode;
    private BigDecimal amount;
    private String currency;
    private String provider;
    private String method;
    private String payerReference;
    private String status;
    private String providerReference;
    private String redirectUrl;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getPayerReference() { return payerReference; }
    public void setPayerReference(String payerReference) { this.payerReference = payerReference; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
