package org.shmmap.manager.config;

import java.util.HashMap;
import java.util.Map;

public class ApplicationConfig {
    private HttpConfig http;
    private RaftConfig raft;
    private MapConfig[] maps;
    private Map<String,MapConfig> _maps = new HashMap<>();

    public RaftConfig getRaft() {
        return raft;
    }

    public void setRaft(RaftConfig raft) {
        this.raft = raft;
    }

    public MapConfig[] getMaps() {
        return maps;
    }

    public void setMaps(MapConfig[] maps) {
        this.maps = maps;
        for(MapConfig mc: maps) {
            _maps.put(mc.getName(), mc);
        }
    }

    public HttpConfig getHttp() {
        return http;
    }

    public void setHttp(HttpConfig http) {
        this.http = http;
    }

    public MapConfig getMapByName(String name) {
        return _maps.get(name);
    }
}
