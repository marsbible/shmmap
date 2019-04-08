package org.shmmap.manager.config;

public class RaftConfig {
    private String data_dir;
    private int election_timeout;
    private String local_addr;
    private int rpc_timeout = 5;
    private String[] peers;

    public String getData_dir() {
        return data_dir;
    }

    public void setData_dir(String data_dir) {
        this.data_dir = data_dir;
    }

    public int getElection_timeout() {
        return election_timeout;
    }

    public void setElection_timeout(int election_timeout) {
        this.election_timeout = election_timeout;
    }

    public String getLocal_addr() {
        return local_addr;
    }

    public void setLocal_addr(String local_addr) {
        this.local_addr = local_addr;
    }

    public int getRpc_timeout() {
        return rpc_timeout;
    }

    public void setRpc_timeout(int rpc_timeout) {
        this.rpc_timeout = rpc_timeout;
    }

    public String[] getPeers() {
        return peers;
    }

    public void setPeers(String[] peers) {
        this.peers = peers;
    }
}
