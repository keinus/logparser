package org.keinus.logparser.domain.parsing.service;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.domain.configuration.model.ParserAdapterConfig;
import org.keinus.logparser.domain.parsing.model.IParser;
import org.keinus.logparser.infrastructure.util.MergingHashMap;
import org.keinus.logparser.domain.model.LogEvent;
import org.springframework.stereotype.Service;
import org.keinus.logparser.infrastructure.config.ApplicationProperties;

/**
 * 원본 로그 텍스트를 구조화된 데이터(Map)로 파싱하는 서비스 클래스입니다.
 */
@Service
public class ParseService {
    private static final Logger LOGGER = LoggerFactory.getLogger( ParseService.class );

    private MergingHashMap<IParser> parsers = new MergingHashMap<>();

    public ParseService(ApplicationProperties applicationProperties) {
        List<ParserAdapterConfig> parserList = applicationProperties.getParser();
        for(ParserAdapterConfig parser : parserList) {
            String parserType = parser.getType();
            IParser parserInterface = loadLibrary(parserType);
            if(parserInterface == null) {
                continue;
            }
            parserInterface.init(parser.getParam());
            var msgType = parser.getMessagetype();
            parsers.put(msgType, parserInterface);
            LOGGER.info("Message Parser registered {}", parserType);
        }
    }

    private IParser loadLibrary(String parserClassName) {
        String className = "org.keinus.logparser.parser." + parserClassName;
        Class<?> testClass;
        try {
            testClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOGGER.error(className + " not found", e);
            return null;
        }
        if (testClass == null || !IParser.class.isAssignableFrom(testClass)) {
            LOGGER.error("{} is not a valid parser class", className);
            return null;
        }
        IParser parserInterface;
        try {
            parserInterface = (IParser) testClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            LOGGER.error("{} can not call instantiated", className);
            return null;
        }
        return parserInterface;
    }

    /**
     * LogEvent를 파싱합니다.
     */
    public boolean parse(LogEvent logEvent) {
        String messageType = logEvent.getMessageType();
        List<IParser> parserList = parsers.get(messageType);

        for(IParser parser : parserList) {
            if(parser.parse(logEvent)) {
                return true;
            }
        }
        return false;
    }
}
