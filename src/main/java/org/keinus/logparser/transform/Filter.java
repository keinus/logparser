package org.keinus.logparser.transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.keinus.logparser.config.TransformParamConfig;
import org.keinus.logparser.interfaces.ITransform;

public class Filter implements ITransform {
    private Map<String, List<String>> pass = new HashMap<>();
    private Map<String, List<String>> drop = new HashMap<>();

    private void parseParam(Map<String, List<String>> paramMap, Map<String, String> param) {
		if(param != null) {
			param.forEach((key, value) -> {
				List<String> values = new ArrayList<>();
                for (String v : value.split(",")) {
                    String trimmed = v.trim();
                    if (!trimmed.isEmpty()) {
                        values.add(trimmed);
                    }
                }
                paramMap.put(key, values);
			});
		}
    }

	@Override
	public void init(TransformParamConfig param) {
        parseParam(this.pass, param.getPass());
        parseParam(this.drop, param.getDrop());
	}

	@Override
	public Map<String, Object> parse(Map<String, Object> message) {
		for(Entry<String, List<String>> entry : drop.entrySet()) {
			String prop = entry.getKey();
			String targetProp = (String) message.get(prop);
			if(entry.getValue().contains(targetProp))
				return Collections.emptyMap();
		}
		for(Entry<String, List<String>> entry : pass.entrySet()) {
			String prop = entry.getKey();
			String targetProp = (String) message.get(prop);
			if(!entry.getValue().contains(targetProp))
				return Collections.emptyMap();
		}
		return message;
	}
}