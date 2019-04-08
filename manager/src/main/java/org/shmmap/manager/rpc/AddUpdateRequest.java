package org.shmmap.manager.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;

public class AddUpdateRequest implements Serializable {
    static ObjectMapper mapper = new ObjectMapper();
    private static final long serialVersionUID = 3133252153499148908L;

    private String map;
    private String key;
    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getMap() {
        return map;
    }

    public void setMap(String map) {
        this.map = map;
    }

    @Override
    public String toString() {
        try {
            return mapper.writer().writeValueAsString(this);
        }
        catch (Exception e) {
            return "{}";
        }
    }
}
