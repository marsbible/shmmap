package org.shmmap.solr.plugin;

import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.ValueSourceParser;

import java.lang.reflect.Method;
import java.util.Set;

public class TestReloadHandler extends RequestHandlerBase {
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        String name = req.getParams().get("name");

        ValueSourceParser parser = req.getCore().getValueSourceParser(name);
        if(parser == null) {
            rsp.add("status", "no valueSource");
        }
        else {
            try {
                Method reload = parser.getClass().getMethod("reload");

                int ret = (int)reload.invoke(parser);
                if(ret == 0) {
                    rsp.add("status", "ok");
                }
                else {
                    rsp.add("status", "reload failed");
                }
            }
            catch (Exception e) {
                rsp.add("status", e.getMessage());
            }
        }
    }

    public String getDescription() {
        return "This is TestReloadHandler that could reload Test map.";
    }
}
