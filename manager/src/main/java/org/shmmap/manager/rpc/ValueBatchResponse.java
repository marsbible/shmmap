package org.shmmap.manager.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;

public class ValueBatchResponse implements Serializable {
    static ObjectMapper mapper = new ObjectMapper();
    private static final long serialVersionUID = 4105986152515334241L;

    private Object[]              values;
    private boolean           success;

    /**
     * redirect peer id
    */
    private String            redirect;
    private String            errorMsg;

    public Object[] getValues() {
        return values;
    }

    public void setValues(Object[] values) {
        this.values = values;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getRedirect() {
        return redirect;
    }

    public void setRedirect(String redirect) {
        this.redirect = redirect;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public ValueBatchResponse(Object[] values, boolean success, String redirect, String errorMsg) {
        super();
        this.values = values;
        this.success = success;
        this.redirect = redirect;
        this.errorMsg = errorMsg;
    }

    public ValueBatchResponse() {
        super();
    }

    @Override
    public String toString() {
        try {
            return "ValueResponse [values=" + mapper.writer().writeValueAsString(values) + ", success=" + this.success + ", redirect=" + this.redirect
                    + ", errorMsg=" + this.errorMsg + "]";
        }
        catch (Exception e) {
            return "ValueResponse [values=" + this.values + ", success=" + this.success + ", redirect=" + this.redirect
                    + ", errorMsg=" + this.errorMsg + "]";
        }
    }
}
