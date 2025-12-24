package com.danavalerie.matrixmudrelay.matrix;

public final class MatrixApiException extends Exception {
    public final int statusCode;
    public final String responseBody;

    public MatrixApiException(int statusCode, String responseBody) {
        super("Matrix API error status=" + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}
