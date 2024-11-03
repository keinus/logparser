package org.keinus.logparser.schema;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class Message implements Serializable {
    private static final long serialVersionUID = 2405172041950251807L;

    @Builder.Default
    private transient Map<String, Object> msg = new HashMap<>();

    private String originText;

    @Builder.Default
    private String type = null;

    public Message() {
        msg = new HashMap<>();
    }
    
    public Message(String type) {
        msg = new HashMap<>();
        this.type = type;
    }

    public Message(String message, String type) {
        msg = new HashMap<>();
        this.originText = message;
        this.type = type;
    }
    
    public void put(String key, Object value) {
        msg.put(key, value);
    }
    
    public void remove(String key) {
        msg.remove(key);
    }
    
    public void putAll(Map<String, Object> obj) {
        msg.putAll(obj);
    }
    
    
}
