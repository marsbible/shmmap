package org.shmmap.manager.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;

public class GetRequest implements Serializable {
    static ObjectMapper mapper = new ObjectMapper();
    private static final long serialVersionUID = 9106541787355691746L;

    private String map;
    private String key;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
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
