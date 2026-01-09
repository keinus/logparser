package org.keinus.logparser.domain.transformation.model;

import org.keinus.logparser.domain.configuration.model.TransformParamConfig;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.transformation.service.StructuredTransformService;
import org.keinus.logparser.infrastructure.util.SpringContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StructuredTransformService를 호출하여 로그 데이터를 정형화된 스키마로 변환하는 ITransform 구현체입니다.
 */
public class Structure implements ITransform {
    private static final Logger log = LoggerFactory.getLogger(Structure.class);
    private StructuredTransformService structuredTransformService;

    @Override
    public void init(TransformParamConfig param) {
        // StructuredTransformService는 Spring Bean이므로 StaticContextAccessor를 통해 가져옵니다.
        try {
            this.structuredTransformService = SpringContextUtil.getBean(StructuredTransformService.class);
        } catch (Exception e) {
            log.error("Failed to get StructuredTransformService bean", e);
        }
    }

    @Override
    public boolean transform(LogEvent logEvent) {
        if (structuredTransformService == null) {
            log.warn("StructuredTransformService is not initialized. Skipping structure transform.");
            return true; // 에러를 내지 않고 통과 (기본 동작 유지)
        }

        return structuredTransformService.applyToLogEvent(logEvent);
    }
}
