package ru.rest.utils;

public class CodeMsgException extends Exception {
    int errorCode;

    public CodeMsgException(int errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
