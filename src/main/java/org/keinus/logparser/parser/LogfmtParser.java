package org.keinus.logparser.parser;

import java.util.Map;


public class LogfmtParser extends RegexParser {
    public LogfmtParser() {
        super();
        super.init("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");
    }

    @Override
    public void init(Object param) {
      // Unnecessary
    }
 
    @Override
    public Map<String, Object> parse(String message) {
        return super.parse(message);
    }
}