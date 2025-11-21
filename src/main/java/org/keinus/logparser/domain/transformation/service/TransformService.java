package org.keinus.logparser.domain.transformation.service;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.domain.configuration.model.TransformConfig;
import org.keinus.logparser.domain.transformation.model.ITransform;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.infrastructure.config.ApplicationProperties;
import org.springframework.stereotype.Service;

/**
 * 파싱된 메시지 데이터에 대해 다양한 변환 작업을 수행하는 서비스 클래스입니다.
 */
@Service
public class TransformService {
    private static final Logger LOGGER = LoggerFactory.getLogger( TransformService.class );

    private Map<String, List<ITransform>> transformer = new HashMap<>();

    private ITransform loadLibrary(String calssName) {
        String className = "org.keinus.logparser.transform." + calssName;
        Class<?> testClass;
        try {
            testClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOGGER.error(className + " not found", e);
            return null;
        }
        if (testClass == null || !ITransform.class.isAssignableFrom(testClass)) {
            LOGGER.error("{} is not a valid transform class", className);
            return null;
        }
        ITransform transformInterface;
        try {
            transformInterface = (ITransform) testClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            LOGGER.error("{} can not call instantiated", className);
            return null;
        }
        if (transformInterface == null || !ITransform.class.isAssignableFrom(transformInterface.getClass())) {
            LOGGER.error("{} is not a valid transform class", className);
            return null;
        }
        return transformInterface;
    }

    public TransformService(ApplicationProperties applicationProperties) {
        List<TransformConfig> transformList = applicationProperties.getTransform();
        for(TransformConfig trans : transformList) {
            ITransform transformInterface = loadLibrary(trans.getType());
            if(transformInterface == null)
               continue;
            transformInterface.init(trans.getParam());
            var msgType = trans.getMessagetype();
            transformer.computeIfAbsent(msgType, k -> new ArrayList<>());
            transformer.get(msgType).add(transformInterface);
            LOGGER.info("Message Parser registerd {}", trans.getType());
        }
    }

    /**
     * LogEvent를 변환합니다.
     */
    public boolean transform(LogEvent logEvent) {
        String messageType = logEvent.getMessageType();
        for(ITransform trans : transformer.getOrDefault(messageType, new ArrayList<>())) {
            if(!trans.transform(logEvent)) {
                return false;
            }
        }
        return true;
    }
}
