package org.shmmap.manager;

import com.alipay.remoting.exception.RemotingException;
import com.alipay.sofa.jraft.*;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.CliOptions;
import com.alipay.sofa.jraft.rpc.impl.cli.BoltCliClientService;
import org.shmmap.manager.config.ApplicationConfig;
import org.shmmap.manager.config.MapConfig;
import org.shmmap.manager.config.RaftConfig;
import org.shmmap.manager.rpc.*;
import org.shmmap.manager.rpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MapClient {
    private static final Logger LOG = LoggerFactory.getLogger(MapClient.class);
    private final int RPC_TIMEOUT_MS;
    private final int REFRESH_TIMEOUT_MS = 6000;
    //配置
    private ApplicationConfig config;

    private RouteTable rt;

    private BoltCliClientService clientService;

    private CliService cliService;

    public MapClient(ApplicationConfig config) throws Exception{
        this.config = config;
        this.rt = RouteTable.getInstance();

        RaftConfig rc = this.config.getRaft();

        clientService = new BoltCliClientService();
        clientService.init(new CliOptions());

        cliService = RaftServiceFactory.createAndInitCliService(new CliOptions());

        for(MapConfig mc: this.config.getMaps()) {
            Configuration initConf = new Configuration();
            String[] peers = mc.getPeers();
            if(peers == null) peers = rc.getPeers();

            for(String str : peers) {
                PeerId pi = PeerId.parsePeer(str);
                if(pi == null) {
                    LOG.error("Invalid peer configuration: {}", str);
                    continue;
                }
                initConf.addPeer(pi);
            }

            this.rt.updateConfiguration(mc.getName(), initConf);
        }

        RPC_TIMEOUT_MS = this.config.getRaft().getRpc_timeout()*1000;
    }

    private PeerId selectLeader(String name) {
        PeerId leader = rt.selectLeader(name);
        if(leader == null) {
            try {
                rt.refreshLeader(clientService, name, REFRESH_TIMEOUT_MS);
                leader = rt.selectLeader(name);
            }
            catch (Exception e) {
                LOG.error("refresh leader failed: ", e);
            }
        }

        return leader;
    }


    public Object addUpdate(String map, final String key, final String value) throws RemotingException, InterruptedException {
        final AddUpdateRequest request = new AddUpdateRequest();
        request.setMap(map);
        request.setKey(key);
        request.setValue(value);

        ValueResponse vr = null;
        do {
            final PeerId leader = selectLeader(map);

            vr = (ValueResponse) clientService.getRpcClient().invokeSync(leader.getEndpoint().toString(), request, RPC_TIMEOUT_MS);

            if (vr == null) {
                LOG.info("map client addUpdate return null");
                break;
            }

            if(vr.getRedirect() != null) {
                rt.updateLeader(map, vr.getRedirect());
            }
        }
        while(vr.getRedirect() != null);


        if(vr != null ) {
            return vr.isSuccess()?vr.getValue():vr.getErrorMsg();
        }

        return null;
    }

    private void updateList(final Object[] ret, int idx, Object o) {
        synchronized (ret) {
            ret[idx] = o;
        }
    }

    public List addUpdateMulti(String map, List<MapServer.KeyValue> items) throws RemotingException, InterruptedException {
        MapConfig mc = config.getMapByName(map);

        return addUpdateMultiSync(map, items, mc.getRpc_batch_size());
    }


    protected List addUpdateMultiSync(String map, List<MapServer.KeyValue> items, final int BATCH_SIZE) throws RemotingException, InterruptedException {
        int rcount = 0;
        int i = 0;
        final List ret = new ArrayList(items.size());


        do {
            final int LEN = Integer.min(BATCH_SIZE, items.size() - i);
            final int END = i + LEN;
            rcount = 0;
            final PeerId leader = selectLeader(map);

            final AddUpdateBatchRequest request = new AddUpdateBatchRequest();
            request.setMap(map);
            AddUpdateBatchRequest.Request[] requests = new AddUpdateBatchRequest.Request[LEN];
            for(int j=0; j<LEN; j++) {
                requests[j] = new AddUpdateBatchRequest.Request(items.get(i + j).getKey(), items.get(i + j).getValue());
            }
            request.setRequests(requests);

            ValueBatchResponse vr = (ValueBatchResponse) clientService.getRpcClient().invokeSync(leader.getEndpoint().toString(), request, RPC_TIMEOUT_MS);

            if (vr == null) {
                LOG.warn("map addUpdateMulti receive null response");
                i = END;
            } else {
                if (vr.getRedirect() != null) {
                    rt.updateLeader(map, vr.getRedirect());
                    LOG.info("map client redirect info detected {}", vr.getRedirect());
                    rcount++;
                }
                else if(vr.isSuccess()){
                    Object[] vv = vr.getValues();
                    for(int j=0; i<END; i++,j++) {
                        ret.add(vv[j]);
                    }
                }
                else {
                    LOG.warn("map addUpdateMulti update failed");
                    for(; i<END; i++) {
                        ret.add(null);
                    }
                }
            }
        }while(rcount > 0 || i < items.size());


        return ret;
    }

    public Object get(String map, final String key) throws RemotingException,InterruptedException {
        final GetRequest request = new GetRequest();
        request.setMap(map);
        request.setKey(key);

        ValueResponse vr;

        do {
            final PeerId leader = selectLeader(map);

            vr = (ValueResponse) clientService.getRpcClient().invokeSync(leader.getEndpoint().toString(), request, RPC_TIMEOUT_MS);

            if (vr == null) {
                LOG.info("map client get return null");
                break;
            }

            if(vr.getRedirect() != null) {
                rt.updateLeader(map, vr.getRedirect());
            }
        }
        while(vr.getRedirect() != null);


        if(vr != null) {
            return vr.isSuccess()?vr.getValue():null;
        }

        return null;
    }

    public List getMulti(String map, List<String> keys) throws RemotingException, InterruptedException {
        int rcount = 0;
        int i = 0;
        final List ret = new ArrayList(keys.size());

        MapConfig mc = config.getMapByName(map);

        do {
            final int LEN = Integer.min(mc.getRpc_batch_size(), keys.size() - i);
            final int END = i + LEN;
            rcount = 0;
            final PeerId leader = selectLeader(map);

            final GetBatchRequest request = new GetBatchRequest();
            request.setMap(map);
            GetBatchRequest.Request[] requests = new GetBatchRequest.Request[LEN];
            for(int j=0; j<LEN; j++) {
                requests[j] = new GetBatchRequest.Request(keys.get(i + j));
            }
            request.setRequests(requests);

            ValueBatchResponse vr = (ValueBatchResponse) clientService.getRpcClient().invokeSync(leader.getEndpoint().toString(), request, RPC_TIMEOUT_MS);

            if (vr == null) {
                LOG.warn("map getMulti receive null response");
                i = END;
            } else {
                if (vr.getRedirect() != null) {
                    rt.updateLeader(map, vr.getRedirect());
                    LOG.info("map client redirect info detected {}", vr.getRedirect());
                    rcount++;
                }
                else if(vr.isSuccess()){
                    Object[] vv = vr.getValues();
                    for(int j=0; i<END; i++,j++) {
                        ret.add(vv[j]);
                    }
                }
                else {
                    LOG.warn("map getMulti failed");
                    for(; i<END; i++) {
                        ret.add(null);
                    }
                }
            }
        }while(rcount > 0 || i < keys.size());


        return ret;
    }

    public String getLeader(String map) {
        //rt.refreshConfiguration(clientService, map, REFRESH_TIMEOUT_MS);
        PeerId leader = new PeerId();

        Status status = cliService.getLeader(map, rt.getConfiguration(map), leader);
        if(status.isOk()) {
            return leader.toString();
        }
        else {
            return null;
        }
    }

    public List<String> getPeers(String map) {
        final List<PeerId> peers = cliService.getPeers(map, rt.getConfiguration(map));

        return peers.stream().map(PeerId::toString).collect(Collectors.toList());
    }

    public String addPeer(String map, String peer) {
        Status status = cliService.addPeer(map, this.rt.getConfiguration(map), PeerId.parsePeer(peer));
        if(status.isOk()) {
            return "ok";
        }
        else {
            return status.getErrorMsg();
        }
    }

    public String removePeer(String map, String peer) {
        Status status = cliService.removePeer(map, this.rt.getConfiguration(map), PeerId.parsePeer(peer));
        if(status.isOk()) {
            return "ok";
        }
        else {
            return status.getErrorMsg();
        }
    }

    /**
     * TODO: 危险操作，重置当前节点的peer配置
    */
    public String resetPeer(String map, List<String> peers) {
        Configuration conf = new Configuration();
        for(String peer: peers) {
            conf.addPeer(PeerId.parsePeer(peer));
        }

        Status status = cliService.resetPeer(map, PeerId.parsePeer(config.getRaft().getLocal_addr()), conf);
        if(status.isOk()) {
            return "ok";
        }
        else {
            return status.getErrorMsg();
        }
    }
}
