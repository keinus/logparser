package org.keinus.logparser.schema;

import java.io.Serializable;
import java.time.Instant;
import lombok.Data;

@Data
public class Message implements Serializable {
    private static final long serialVersionUID = 2405172041950251807L;
    private String originText;
    private String type;
    private Instant timestamp;
    private String host;

    public Message(String originText, String host) {
        this.originText = originText;
        this.host = host;
    }
}
