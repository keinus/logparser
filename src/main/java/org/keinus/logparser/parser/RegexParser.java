package org.keinus.logparser.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.keinus.logparser.interfaces.IParser;


public class RegexParser implements IParser {
    private Pattern regex = null;

    @Override
    public void init(Object param) {
        String regex = (String)param;
        this.regex = Pattern.compile(regex);
    }
 
    @Override
    public Map<String, Object> parse(String message) {
        Matcher m = regex.matcher(message);
        Map<String, Object> map = new HashMap<>();
        while(m.find()){
            map.put(m.group(1), m.group(2));
        }
        return map;
    }
}
