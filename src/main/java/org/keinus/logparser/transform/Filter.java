package org.keinus.logparser.transform;

import java.util.Arrays;
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
				String[] targetSplit = value.split(",");
				paramMap.put(key, Arrays.asList(targetSplit));
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
				return null;
		}
		for(Entry<String, List<String>> entry : pass.entrySet()) {
			String prop = entry.getKey();
			String targetProp = (String) message.get(prop);
			if(!entry.getValue().contains(targetProp))
				return null;
		}
		return message;
	}
}