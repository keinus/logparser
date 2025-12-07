package org.keinus.logparser.application.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.keinus.logparser.application.pipeline.PipelineReloadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 설정 변경 시 자동으로 파이프라인을 재시작하는 AOP 컴포넌트
 * <p>
 * ConfigManagementService의 설정 변경 메서드 실행 후 자동으로 파이프라인을 리로드합니다.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "logparser.auto-reload", havingValue = "true", matchIfMissing = false)
public class ConfigChangeListener {

    private final PipelineReloadService pipelineReloadService;

    @Value("${logparser.auto-reload-delay-ms:2000}")
    private long autoReloadDelayMs;

    /**
     * Input Adapter 생성/수정/삭제 시 자동 리로드
     */
    @AfterReturning(
        pointcut = "execution(* org.keinus.logparser.application.config.ConfigManagementService.createInputAdapter(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.updateInputAdapter(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.deleteInputAdapter(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.enableInputAdapter(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.disableInputAdapter(..))"
    )
    public void onInputAdapterChange() {
        log.info("Input Adapter configuration changed, scheduling pipeline reload in {}ms", autoReloadDelayMs);
        scheduleReload("InputAdapter");
    }

    /**
     * Parser 생성/수정/삭제 시 자동 리로드
     */
    @AfterReturning(
        pointcut = "execution(* org.keinus.logparser.application.config.ConfigManagementService.createParser(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.updateParser(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.deleteParser(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.updateParserPriority(..))"
    )
    public void onParserChange() {
        log.info("Parser configuration changed, scheduling pipeline reload in {}ms", autoReloadDelayMs);
        scheduleReload("Parser");
    }

    /**
     * Transform 생성/수정/삭제 시 자동 리로드
     */
    @AfterReturning(
        pointcut = "execution(* org.keinus.logparser.application.config.ConfigManagementService.createTransform(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.updateTransform(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.deleteTransform(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.updateTransformPriority(..))"
    )
    public void onTransformChange() {
        log.info("Transform configuration changed, scheduling pipeline reload in {}ms", autoReloadDelayMs);
        scheduleReload("Transform");
    }

    /**
     * Output Adapter 생성/수정/삭제 시 자동 리로드
     */
    @AfterReturning(
        pointcut = "execution(* org.keinus.logparser.application.config.ConfigManagementService.createOutputAdapter(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.updateOutputAdapter(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.deleteOutputAdapter(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.enableOutputAdapter(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.disableOutputAdapter(..))"
    )
    public void onOutputAdapterChange() {
        log.info("Output Adapter configuration changed, scheduling pipeline reload in {}ms", autoReloadDelayMs);
        scheduleReload("OutputAdapter");
    }

    /**
     * 공통 설정 변경 시 자동 리로드
     */
    @AfterReturning(
        pointcut = "execution(* org.keinus.logparser.application.config.ConfigManagementService.updateCommonSettings(..)) || " +
                   "execution(* org.keinus.logparser.application.config.ConfigManagementService.setConfigValue(..))"
    )
    public void onCommonSettingsChange() {
        log.info("Common settings changed, scheduling pipeline reload in {}ms", autoReloadDelayMs);
        scheduleReload("CommonSettings");
    }

    /**
     * 설정 리로드를 비동기로 스케줄링
     *
     * @param configType 변경된 설정 타입
     */
    private void scheduleReload(String configType) {
        // 별도 스레드에서 지연 후 리로드 실행
        new Thread(() -> {
            try {
                // 설정 변경 후 잠시 대기 (트랜잭션 완료 보장)
                Thread.sleep(autoReloadDelayMs);

                // 리로드 실행
                log.info("Auto-reloading pipeline after {} configuration change", configType);
                pipelineReloadService.validateAndReload();
                log.info("Pipeline reload completed successfully after {} configuration change", configType);

            } catch (InterruptedException e) {
                log.warn("Pipeline reload interrupted", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Failed to auto-reload pipeline after {} configuration change: {}",
                    configType, e.getMessage(), e);
            }
        }, "ConfigChangeReloader-" + configType).start();
    }
}
