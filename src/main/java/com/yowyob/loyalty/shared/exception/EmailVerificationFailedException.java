package com.yowyob.loyalty.shared.exception;

public class EmailVerificationFailedException extends AppException {
    public EmailVerificationFailedException(String detail) {
        super(ErrorCode.EMAIL_VERIFICATION_FAILED, detail);
    }
}
