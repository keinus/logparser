package org.keinus.logparser.domain.output.model;

/**
 * 출력 어댑터 전송 실패를 상위 파이프라인으로 전파하기 위한 런타임 예외입니다.
 */
public class OutputDeliveryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OutputDeliveryException(String message) {
        super(message);
    }

    public OutputDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
