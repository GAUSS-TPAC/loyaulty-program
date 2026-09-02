package com.yowyob.loyalty.shared.exception;

public class PasswordResetFailedException extends AppException {
    public PasswordResetFailedException(String detail) {
        super(ErrorCode.PASSWORD_RESET_FAILED, detail);
    }
}
