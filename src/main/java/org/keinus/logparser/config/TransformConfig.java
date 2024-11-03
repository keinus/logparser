package org.keinus.logparser.config;

import lombok.Data;

@Data
public class TransformConfig {
    String type;
    String messagetype;
    TransformParamConfig param;
}
