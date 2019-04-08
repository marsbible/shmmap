package org.shmmap.manager.config;

public class MapConfig {
    private String name;
    private String location;
    private int max_size;

    private String key_class;
    private int key_size;

    private String val_class;
    private int val_size;

    private boolean ignore_update_error = false;

    private int snapshot_interval;
    private int rpc_batch_size = 5;
    private String[] peers;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getMax_size() {
        return max_size;
    }

    public void setMax_size(int max_size) {
        this.max_size = max_size;
    }

    public String getKey_class() {
        return key_class;
    }

    public void setKey_class(String key_class) {
        this.key_class = key_class;
    }

    public int getKey_size() {
        return key_size;
    }

    public void setKey_size(int key_size) {
        this.key_size = key_size;
    }

    public String getVal_class() {
        return val_class;
    }

    public void setVal_class(String val_class) {
        this.val_class = val_class;
    }

    public int getVal_size() {
        return val_size;
    }

    public void setVal_size(int val_size) {
        this.val_size = val_size;
    }

    public boolean isIgnore_update_error() {
        return ignore_update_error;
    }

    public void setIgnore_update_error(boolean ignore_update_error) {
        this.ignore_update_error = ignore_update_error;
    }

    public int getRpc_batch_size() {
        return rpc_batch_size;
    }

    public void setRpc_batch_size(int rpc_batch_size) {
        if(rpc_batch_size > 1000) rpc_batch_size = 1000;
        if(rpc_batch_size < 5) rpc_batch_size = 5;
        this.rpc_batch_size = rpc_batch_size;
    }

    public int getSnapshot_interval() {
        return snapshot_interval;
    }

    public void setSnapshot_interval(int snapshot_interval) {
        this.snapshot_interval = snapshot_interval;
    }

    public String[] getPeers() {
        return peers;
    }

    public void setPeers(String[] peers) {
        this.peers = peers;
    }
}
