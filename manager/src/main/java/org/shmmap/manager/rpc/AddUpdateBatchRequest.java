package org.shmmap.manager.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;

public class AddUpdateBatchRequest implements Serializable {
    public static class Request implements Serializable {
        private static final long serialVersionUID = 6160101054689963535L;
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

        public Request(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    static ObjectMapper mapper = new ObjectMapper();
    private static final long serialVersionUID = 5331561845756227520L;

    private String map;
    private Request[] requests;

    public String getMap() {
        return map;
    }

    public void setMap(String map) {
        this.map = map;
    }

    public Request[] getRequests() {
        return requests;
    }

    public void setRequests(Request[] requests) {
        this.requests = requests;
    }

    public void Request(Request[] requests) {
        this.requests = requests;
    }

    @Override
    public String toString() {
        try {
            return mapper.writer().writeValueAsString(requests);
        }
        catch (Exception e) {
            return "[]";
        }
    }
}
