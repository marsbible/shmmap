package org.shmmap.manager.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;

public class GetBatchRequest implements Serializable {
    public static class Request implements Serializable {
        private static final long serialVersionUID = -458579158066117793L;

        private String key;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public Request(String key) {
            this.key = key;
        }
    }


    static ObjectMapper mapper = new ObjectMapper();
    private static final long serialVersionUID = -5054955723029238716L;

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
