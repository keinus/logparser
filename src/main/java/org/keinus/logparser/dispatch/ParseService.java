package org.keinus.logparser.dispatch;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.interfaces.IParser;
import org.keinus.logparser.util.MergingHashMap;


public class ParseService {
    private static final Logger LOGGER = LoggerFactory.getLogger( ParseService.class );

    private MergingHashMap<IParser> parsers = new MergingHashMap<>();

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

    public ParseService(List<Map<String, String>> parserList) {
        for(Map<String, String> parser : parserList) {
            String parserType = parser.get("type");
            IParser parserInterface = loadLibrary(parserType);
            if(parserInterface == null) {
                continue;
            }
            parserInterface.init(parser.get("param"));
            var msgType = parser.get("messagetype");
            parsers.put(msgType, parserInterface);
            LOGGER.info("Message Parser registered {}", parserType);
        }
    }

    public Map<String, Object> parse(String text, String type) {
        List<IParser> parserList = parsers.get(type);

        for(IParser parser : parserList) {
            var parsered = parser.parse(text);
            if(parsered != null) {
                return parsered;
            }
        }
        return new HashMap<>();
    }
}
