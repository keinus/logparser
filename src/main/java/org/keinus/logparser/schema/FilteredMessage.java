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
public class FilteredMessage implements Serializable {
    private static final long serialVersionUID = 2405172041950251807L;
    private String originText;
    private String type;

    @Builder.Default
    private Map<String, Object> msg = new HashMap<>();

    public void fromMessage(Message message) {
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
