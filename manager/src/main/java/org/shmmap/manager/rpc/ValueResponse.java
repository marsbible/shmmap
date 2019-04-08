package org.shmmap.manager.rpc;

import java.io.Serializable;

public class ValueResponse implements Serializable {
    private static final long serialVersionUID = 4105986152515334241L;

    private Object              value;
    private boolean           success;

    /**
     * redirect peer id
    */
    private String            redirect;
    private String            errorMsg;

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
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

    public ValueResponse(Object value, boolean success, String redirect, String errorMsg) {
        super();
        this.value = value;
        this.success = success;
        this.redirect = redirect;
        this.errorMsg = errorMsg;
    }

    public ValueResponse() {
        super();
    }

    @Override
    public String toString() {
        return "ValueResponse [value=" + this.value.toString() + ", success=" + this.success + ", redirect=" + this.redirect
                + ", errorMsg=" + this.errorMsg + "]";
    }
}
