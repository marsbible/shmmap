package org.shmmap.manager.rpc;

import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.SyncUserProcessor;
import org.shmmap.manager.MapEngine;
import org.shmmap.manager.MapServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetRequestProcessor extends SyncUserProcessor<GetRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(GetRequestProcessor.class);

    private MapServer mapServer;

    public GetRequestProcessor(MapServer mapServer) {
        super();
        this.mapServer = mapServer;
    }

    @Override
    public Object handleRequest(final BizContext bizCtx, final GetRequest request) throws Exception {
        MapEngine mapEngine = this.mapServer.getEngine(request.getMap());

        if(mapEngine == null) {
            return null;
        }

        if (!mapEngine.getFsm().isLeader()) {
            return mapEngine.redirect();
        }

        final ValueResponse response = new ValueResponse();

        try {
            Object v = mapEngine.getFsm().getValue(request.getKey());
            response.setValue(v);
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
        return GetRequest.class.getName();
    }
}
