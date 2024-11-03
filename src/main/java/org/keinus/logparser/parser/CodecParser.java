package org.keinus.logparser.parser;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

public class CodecParser {
    
    // @Builder.Default
    private SimpleDateFormat transFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'000Z'");

    private CodecParser() {
        throw new IllegalStateException("Utility class");
    }

    private static Gson gson = new Gson();
    private static Pattern fortigate = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");

    public static Map<String, Object> parse(String str, String codec) {
		if(codec == null)
			codec = "json";
		
		switch(codec) {
            case "fortigate":
                Matcher m = fortigate.matcher(str);
                Map<String, Object> map = new HashMap<>();
                while(m.find()){
                    map.put(m.group(1), m.group(2));
                }
                return map;
            case "rfc3164":
                
            case "json":
            default:
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                return gson.fromJson(str, type);
		}
	}
}
