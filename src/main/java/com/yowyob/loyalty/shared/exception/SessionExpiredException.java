package com.yowyob.loyalty.shared.exception;

public class SessionExpiredException extends AppException {
    public SessionExpiredException(String detail) {
        super(ErrorCode.SESSION_EXPIRED, detail);
    }
}
