package org.keinus.logparser.transform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.keinus.logparser.config.TransformParamConfig;
import org.keinus.logparser.interfaces.ITransform;

public class AddProperty implements ITransform {
    private Map<String, List<String>> props = null;

    @Override
	public void init(TransformParamConfig param) {
        this.props = param.getAdd();
	}

	@Override
	public Map<String, Object> parse(Map<String, Object> message) {
		for(Entry<String, List<String>> entry : props.entrySet()) {
			String prop = entry.getKey();
			List<String> values = entry.getValue();
            Map<String, Object> target = new HashMap<>();
            message.put(prop, target);

            for(String attr_name : values) {
                target.put(attr_name, message.get(attr_name));
            }
		}
		return message;
	}
}
