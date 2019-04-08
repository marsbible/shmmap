package org.shmmap.manager.rpc;

import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;
import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.entity.Task;
import org.shmmap.manager.AddUpdateClosure;
import org.shmmap.manager.MapEngine;
import org.shmmap.manager.MapServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class AddUpdateRequestProcessor extends AsyncUserProcessor<AddUpdateRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(AddUpdateRequestProcessor.class);

    private MapServer mapServer;

    public AddUpdateRequestProcessor(MapServer mapServer) {
        super();
        this.mapServer = mapServer;
    }

    @Override
    public void handleRequest(final BizContext bizCtx, final AsyncContext asyncCtx, final AddUpdateRequest request) {
        MapEngine mapEngine = this.mapServer.getEngine(request.getMap());
        final ValueResponse response = new ValueResponse();

        if(mapEngine == null) {
            response.setSuccess(false);
            response.setErrorMsg("no map name found " + request.getMap());
            asyncCtx.sendResponse(response);
            return;
        }

        if (!mapEngine.getFsm().isLeader()) {
            asyncCtx.sendResponse(mapEngine.redirect());
            return;
        }


        final AddUpdateClosure closure = new AddUpdateClosure(mapEngine, request, response,
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
            LOG.error("Fail to encode AddUpdateRequest", e);
            response.setSuccess(false);
            response.setErrorMsg(e.getMessage());
            asyncCtx.sendResponse(response);
        }
    }

    @Override
    public String interest() {
        return AddUpdateRequest.class.getName();
    }
}
