package org.shmmap.manager;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import org.shmmap.manager.rpc.AddUpdateRequest;
import org.shmmap.manager.rpc.ValueResponse;

public class AddUpdateClosure implements Closure {
    @SuppressWarnings({ "FieldCanBeLocal", "unused" })
    private MapEngine   mapEngine;
    private AddUpdateRequest    request;
    private ValueResponse response;
    private Closure done;

    public AddUpdateClosure(MapEngine mapEngine, AddUpdateRequest request, ValueResponse response,
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

    public AddUpdateRequest getRequest() {
        return this.request;
    }

    public void setRequest(AddUpdateRequest request) {
        this.request = request;
    }

    public ValueResponse getResponse() {
        return this.response;
    }
}
