package org.keinus.logparser.parser;

import java.util.HashMap;
import java.util.Map;

import org.keinus.logparser.interfaces.IParser;

import io.krakens.grok.api.Grok;
import io.krakens.grok.api.GrokCompiler;
import io.krakens.grok.api.Match;


public class GrokParser implements IParser {
    private GrokCompiler grokCompiler = GrokCompiler.newInstance();
	Grok grok = null;
	
	public GrokParser() {
		grokCompiler.registerDefaultPatterns();
	}
	
	public void init(Object param) {
		String reg = (String)param;
		grok = grokCompiler.compile(reg);
	}
	
	@Override
	public Map<String, Object> parse(String message) {
		if(message == null || message.equals("")) 
            return new HashMap<>();
		
		Match gm = grok.match(message);
		if(gm.isNull().booleanValue())
			return new HashMap<>();
		return gm.capture();
	}
}
