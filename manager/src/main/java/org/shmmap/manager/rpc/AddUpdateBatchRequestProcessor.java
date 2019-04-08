package org.shmmap.manager.rpc;

import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;
import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.entity.Task;
import org.shmmap.manager.AddUpdateBatchClosure;
import org.shmmap.manager.MapEngine;
import org.shmmap.manager.MapServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class AddUpdateBatchRequestProcessor extends AsyncUserProcessor<AddUpdateBatchRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(AddUpdateBatchRequestProcessor.class);

    private MapServer mapServer;

    public AddUpdateBatchRequestProcessor(MapServer mapServer) {
        super();
        this.mapServer = mapServer;
    }

    @Override
    public void handleRequest(final BizContext bizCtx, final AsyncContext asyncCtx, final AddUpdateBatchRequest request) {
        MapEngine mapEngine = this.mapServer.getEngine(request.getMap());
        final ValueBatchResponse response = new ValueBatchResponse();

        if(mapEngine == null) {
            response.setSuccess(false);
            response.setErrorMsg("no map name found " + request.getMap());
            asyncCtx.sendResponse(response);
            return;
        }

        if (!mapEngine.getFsm().isLeader()) {
            asyncCtx.sendResponse(mapEngine.redirectBatch());
            return;
        }


        final AddUpdateBatchClosure closure = new AddUpdateBatchClosure(mapEngine, request, response,
                status -> {
                    if (!status.isOk()) {
                        response.setErrorMsg(status.getErrorMsg());
                        response.setSuccess(false);
                    }
                    asyncCtx.sendResponse(response);
                });

        try {
            final Task task = new Task();
            task.setDone(closure);
            task.setData(ByteBuffer
                    .wrap(SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request)));

            // apply task to raft group.
            mapEngine.getNode().apply(task);
        } catch (final CodecException e) {
            LOG.error("Fail to encode AddUpdateBatchRequest", e);
            response.setSuccess(false);
            response.setErrorMsg(e.getMessage());
            asyncCtx.sendResponse(response);
        }
    }

    @Override
    public String interest() {
        return AddUpdateBatchRequest.class.getName();
    }
}
