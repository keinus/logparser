package org.keinus.logparser.dispatch;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keinus.logparser.interfaces.IParser;


public class ParseService {
    private static final Logger LOGGER = LoggerFactory.getLogger( ParseService.class );

    private Map<String, List<IParser>> parsers = new HashMap<>();

    public ParseService(List<Map<String, String>> parserList) {
        if(parserList != null) {
            for(Map<String, String> parser : parserList) {
                String className = "org.keinus.logparser.parser." + parser.get("type");
                try {
                    Class<?> testClass = Class.forName(className);
                    IParser parserInterface = (IParser) testClass.getDeclaredConstructor().newInstance();
                    parserInterface.init(parser.get("param"));
                    var typeList = parser.get("messagetype").split(",");
                    for(String type : typeList) {
                        if(parsers.get(type) == null) {
                            parsers.put(type, new ArrayList<>());
                        } 
                        parsers.get(type).add(parserInterface);
                    }
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    e.printStackTrace();
                    continue;
                }
                
                LOGGER.info("Message Parser registerd {}", className);
            }
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
