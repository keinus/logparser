package org.keinus.logparser.interfaces;

import java.util.Map;

import org.keinus.logparser.config.TransformParamConfig;

public interface ITransform {
    public void init(TransformParamConfig param);
    public Map<String, Object> parse(Map<String, Object> message);
}
