package org.shmmap.manager.rpc;

import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.SyncUserProcessor;
import org.shmmap.manager.MapEngine;
import org.shmmap.manager.MapServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetBatchRequestProcessor extends SyncUserProcessor<GetBatchRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(GetBatchRequestProcessor.class);

    private MapServer mapServer;

    public GetBatchRequestProcessor(MapServer mapServer) {
        super();
        this.mapServer = mapServer;
    }

    @Override
    public Object handleRequest(final BizContext bizCtx, final GetBatchRequest request) throws Exception {
        MapEngine mapEngine = this.mapServer.getEngine(request.getMap());

        if(mapEngine == null) {
            return null;
        }

        if (!mapEngine.getFsm().isLeader()) {
            return mapEngine.redirectBatch();
        }

        final ValueBatchResponse response = new ValueBatchResponse();

        try {
            Object[] items = new Object[request.getRequests().length];
            int i = 0;
            for(GetBatchRequest.Request r : request.getRequests()) {
                Object v = mapEngine.getFsm().getValue(r.getKey());
                items[i++] = v;
            }

            response.setValues(items);
            response.setSuccess(true);
        }
        catch (Exception e) {
            response.setSuccess(false);
            response.setErrorMsg(e.getMessage());
        }

        return response;
    }

    @Override
    public String interest() {
        return GetBatchRequest.class.getName();
    }
}
