package org.keinus.logparser.domain.parse.service;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.domain.configuration.model.ParserAdapterConfig;
import org.keinus.logparser.domain.configuration.service.DatabaseConfigLoader;
import org.keinus.logparser.infrastructure.util.MergingHashMap;
import org.keinus.logparser.domain.model.LogEvent;
import org.keinus.logparser.domain.parse.model.IParser;
import org.springframework.stereotype.Service;
import org.keinus.logparser.infrastructure.config.ApplicationProperties;

/**
 * 원본 로그 텍스트를 구조화된 데이터(Map)로 파싱하는 서비스 클래스입니다.
 */
@Service
public class ParseService {
    private static final Logger LOGGER = LoggerFactory.getLogger( ParseService.class );

    private MergingHashMap<IParser> parsers = new MergingHashMap<>();
    private final DatabaseConfigLoader databaseConfigLoader;

    public ParseService(ApplicationProperties applicationProperties, DatabaseConfigLoader databaseConfigLoader) {
        this.databaseConfigLoader = databaseConfigLoader;

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

    /**
     * 데이터베이스에서 파서 설정을 다시 로드합니다.
     */
    public synchronized void reload() {
        LOGGER.info("Reloading parsers from database");

        // 기존 파서 초기화
        MergingHashMap<IParser> newParsers = new MergingHashMap<>();

        try {
            DatabaseConfigLoader.PipelineConfiguration config = databaseConfigLoader.loadConfiguration();
            List<ParserAdapterConfig> parserList = config.getParser();

            for(ParserAdapterConfig parser : parserList) {
                String parserType = parser.getType();
                IParser parserInterface = loadLibrary(parserType);
                if(parserInterface == null) {
                    continue;
                }
                parserInterface.init(parser.getParam());
                var msgType = parser.getMessagetype();
                newParsers.put(msgType, parserInterface);
                LOGGER.info("Message Parser reloaded: {}", parserType);
            }

            // 새 파서로 교체
            this.parsers = newParsers;
            LOGGER.info("Parser reload completed: {} parsers loaded", parserList.size());

        } catch (Exception e) {
            LOGGER.error("Failed to reload parsers", e);
            throw new RuntimeException("Failed to reload parsers", e);
        }
    }

    private IParser loadLibrary(String parserClassName) {
        String className = "org.keinus.logparser.domain.parsing.model." + parserClassName;
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

        if (parserList.isEmpty()) {
            return true;
        }

        for(IParser parser : parserList) {
            if(parser.parse(logEvent)) {
                return true;
            }
        }
        return false;
    }
}
