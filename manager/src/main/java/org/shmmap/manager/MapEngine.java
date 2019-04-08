package org.shmmap.manager;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.entity.PeerId;
import com.codahale.metrics.Metric;
import org.shmmap.manager.rpc.ValueBatchResponse;
import org.shmmap.manager.rpc.ValueResponse;

import java.util.Map;
import java.util.Set;

public class MapEngine {
    // jraft 服务端服务框架
    private RaftGroupService raftGroupService;

    // raft 节点
    private Node node;

    // 业务状态机
    private MapStateMachine fsm;

    public RaftGroupService getRaftGroupService() {
        return raftGroupService;
    }

    public Node getNode() {
        return node;
    }

    public MapStateMachine getFsm() {
        return fsm;
    }

    public ValueResponse redirect() {
        final ValueResponse response = new ValueResponse();
        response.setSuccess(false);
        if (this.node != null) {
            final PeerId leader = this.node.getLeaderId();
            if (leader != null) {
                response.setRedirect(leader.toString());
            }
        }

        return response;
    }

    public ValueBatchResponse redirectBatch() {
        final ValueBatchResponse response = new ValueBatchResponse();
        response.setSuccess(false);
        if (this.node != null) {
            final PeerId leader = this.node.getLeaderId();
            if (leader != null) {
                response.setRedirect(leader.toString());
            }
        }
        return response;
    }

    public MapEngine(RaftGroupService raftGroupService, Node node, MapStateMachine fsm) {
        this.raftGroupService = raftGroupService;
        this.node = node;
        this.fsm = fsm;
    }

    public Set<String> getMetricKeys() {
        Map<String, Metric> mm = node.getNodeMetrics().getMetrics();


        return mm.keySet();
    }

    public Metric getMetric(String key) {
        Map<String, Metric> mm = node.getNodeMetrics().getMetrics();

        return mm.get(key);
    }
}
