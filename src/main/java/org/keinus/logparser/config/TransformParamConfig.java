package org.keinus.logparser.config;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class TransformParamConfig {
    Map<String, String> pass;
    Map<String, String> drop;
    Map<String, List<String>> add;
    List<String> remove;
}
