package com.yowyob.loyalty.infrastructure.persistence.bonification.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("bonification_transactions")
public class BonificationTransactionEntity {

    @Id
    private UUID id;
    private UUID tenantId;
    private String transactionId;
    private String clientLogin;
    private BigDecimal amount;
    private boolean debit;
    private String status;
    private boolean succeeded;
    private String message;
    private Instant submittedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getClientLogin() { return clientLogin; }
    public void setClientLogin(String clientLogin) { this.clientLogin = clientLogin; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public boolean isDebit() { return debit; }
    public void setDebit(boolean debit) { this.debit = debit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isSucceeded() { return succeeded; }
    public void setSucceeded(boolean succeeded) { this.succeeded = succeeded; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
