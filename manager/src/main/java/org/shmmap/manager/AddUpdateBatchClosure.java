package org.shmmap.manager;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import org.shmmap.manager.rpc.AddUpdateBatchRequest;
import org.shmmap.manager.rpc.ValueBatchResponse;

public class AddUpdateBatchClosure implements Closure {
    @SuppressWarnings({ "FieldCanBeLocal", "unused" })
    private MapEngine   mapEngine;
    private AddUpdateBatchRequest request;
    private ValueBatchResponse response;
    private Closure done;

    public AddUpdateBatchClosure(MapEngine mapEngine, AddUpdateBatchRequest request, ValueBatchResponse response,
                                 Closure done) {
        super();
        this.mapEngine = mapEngine;
        this.request = request;
        this.response = response;
        this.done = done;
    }

    @Override
    public void run(Status status) {
        if (this.done != null) {
            done.run(status);
        }
    }

    public AddUpdateBatchRequest getRequest() {
        return this.request;
    }

    public void setRequest(AddUpdateBatchRequest request) {
        this.request = request;
    }

    public ValueBatchResponse getResponse() {
        return this.response;
    }
}
