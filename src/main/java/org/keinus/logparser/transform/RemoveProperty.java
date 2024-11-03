package org.keinus.logparser.transform;

import java.util.List;
import java.util.Map;

import org.keinus.logparser.config.TransformParamConfig;
import org.keinus.logparser.interfaces.ITransform;

public class RemoveProperty implements ITransform {
    private List<String> props = null;

    @Override
	public void init(TransformParamConfig param) {
        this.props = param.getRemove();
	}

	@Override
	public Map<String, Object> parse(Map<String, Object> message) {
		for(String entry : props) {
			message.remove(entry);
		}
		return message;
	}
}
