package org.keinus.logparser.schema;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class FilteredMessage {
    private String originText;
    private String type;
    private Map<String, Object> msg;

    public FilteredMessage(String origin, Map<String, Object> body, String type) {
        msg = new HashMap<>();

        this.originText = origin;
        this.msg.putAll(body);
        this.type = type;
    }

    public FilteredMessage(Message message) {
        this.originText = message.getOriginText();
        msg.put("host", message.getHost());
        msg.put("type", message.getType());
        msg.put("@timestamp", message.getTimestamp().toEpochMilli());
        this.type = message.getType();
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
