package org.keinus.logparser.interfaces;

import java.util.Map;


public interface IParser {
    public void init(Object param);
    public Map<String, Object> parse(String message);
}
