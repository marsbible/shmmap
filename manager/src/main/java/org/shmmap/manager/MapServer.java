package org.shmmap.manager;

import com.alipay.remoting.rpc.RpcServer;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.TimerManager;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import com.alipay.sofa.jraft.storage.impl.RocksDBLogStorage;
import com.alipay.sofa.jraft.util.StorageOptionsFactory;
import com.codahale.metrics.Metric;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.shmmap.manager.config.ApplicationConfig;
import org.shmmap.manager.config.MapConfig;
import org.shmmap.manager.config.RaftConfig;
import org.shmmap.manager.rpc.AddUpdateBatchRequestProcessor;
import org.shmmap.manager.rpc.AddUpdateRequestProcessor;
import org.shmmap.manager.rpc.GetBatchRequestProcessor;
import org.shmmap.manager.rpc.GetRequestProcessor;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.io.FileUtils;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompressionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MapServer {
    final static List ITEMS = new ArrayList();
    private static final Logger LOG = LoggerFactory.getLogger(MapServer.class);

    public static class KeyValue {
        private String key;
        private String value;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public KeyValue(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    public static class KeyValueDeserializer extends StdDeserializer<KeyValue> {

        public KeyValueDeserializer() {
            this(null);
        }

        public KeyValueDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public KeyValue deserialize(JsonParser jp, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            JsonNode node = jp.getCodec().readTree(jp);

            String key = node.get("key").asText();
            String value = null;

            if(!node.get("value").isNull()) {
                value = node.get("value").toString();
            }

            return new KeyValue(key, value);
        }
    }

    static class HttpResponse {
        private int status;
        private String msg;
        private List items;

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public List getItems() {
            return items;
        }

        public void setItems(List items) {
            this.items = items;
        }

        public HttpResponse(int status, String msg, List items) {
            this.status = status;
            this.msg = msg;
            this.items = items;
        }
    }

    static class HttpMapResponse {
        private int status;
        private String msg;
        private Map items;

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public Map getItems() {
            return items;
        }

        public void setItems(Map items) {
            this.items = items;
        }

        public HttpMapResponse(int status, String msg, Map items) {
            this.status = status;
            this.msg = msg;
            this.items = items;
        }
    }

    final static ObjectMapper mapper = new ObjectMapper();

    //map
    private Map<String,MapEngine> maps = new HashMap<>();

    //配置
    private ApplicationConfig config;

    public MapServer(ApplicationConfig config) throws IOException {
        this.config = config;
        //定时器
        TimerManager tm = new TimerManager();
        tm.init(30);

        //初始话raft
        RaftConfig rc = config.getRaft();

        // 解析参数
        PeerId serverId = new PeerId();
        if (!serverId.parse(rc.getLocal_addr())) {
            throw new IllegalArgumentException("Fail to parse serverId:" + rc.getLocal_addr());
        }

        // 这里让 raft RPC 和业务 RPC 使用同一个 RPC server, 通常也可以分开.
        RpcServer rpcServer = new RpcServer(serverId.getIp(), serverId.getPort());
        RaftRpcServerFactory.addRaftRequestProcessors(rpcServer);

        // 初始化 raft group 服务框架
        for(MapConfig mc: config.getMaps()) {
            Configuration initConf = new Configuration();
            String[] peers = mc.getPeers();
            if(peers == null) peers = rc.getPeers();

            for(String str : peers) {
                PeerId pi = PeerId.parsePeer(str);
                if(pi == null) {
                    LOG.error("Invalid peer configuration: {}", str);
                    continue;
                }
                initConf.addPeer(pi);
            }

            String dir = rc.getData_dir() + File.separator + mc.getName();
            //解析node option
            NodeOptions nodeOptions = new NodeOptions();

            nodeOptions.setElectionTimeoutMs(rc.getElection_timeout()*1000);
            nodeOptions.setInitialConf(initConf);
            nodeOptions.setSnapshotIntervalSecs(mc.getSnapshot_interval());
            nodeOptions.setLogUri(dir + File.separator + "log");
            nodeOptions.setSnapshotUri(dir + File.separator + "snapshot");
            nodeOptions.setRaftMetaUri(dir + File.separator + "raft_meta");

            // 初始化路径
            FileUtils.forceMkdir(new File(dir));

            MapStateMachine fsm = new MapStateMachine(mc);
            nodeOptions.setFsm(fsm);
            nodeOptions.setEnableMetrics(true);

            RaftGroupService service = new RaftGroupService(mc.getName(), serverId, nodeOptions, rpcServer);
            Node node = service.start(false);

            MapEngine me = new MapEngine(service, node, fsm);
            maps.put(mc.getName(), new MapEngine(service, node, fsm));
        }

        // 注册业务处理器
        rpcServer.registerUserProcessor(new GetRequestProcessor(this));
        rpcServer.registerUserProcessor(new AddUpdateRequestProcessor(this));
        rpcServer.registerUserProcessor(new GetBatchRequestProcessor(this));
        rpcServer.registerUserProcessor(new AddUpdateBatchRequestProcessor(this));

        rpcServer.start();
    }

    public MapEngine getEngine(String name) {
        return maps.get(name);
    }

    private static void initRest(ApplicationConfig config, MapServer ms) throws Exception{
        MapClient mc = new MapClient(config);
        VertxOptions vo = new VertxOptions();
        vo.setWorkerPoolSize(config.getHttp().getMax_workers());

        Vertx vertx = Vertx.vertx(vo);

        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        /**
         * 业务api，用来操作key-value map
         */
        //从本地map中读取值，不保证实时一致性，性能高
        router.get("/maps/:map/:key").handler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            MapEngine me = ms.getEngine(req.getParam("map"));
            Object v;
            res.putHeader("content-type", "application/json");

            try {
                if (me == null || (v = me.getFsm().getValue(req.getParam("key"))) == null) {
                    res.setStatusCode(404);

                    res.end(mapper.writeValueAsString(new HttpResponse(404, "map or key not found", ITEMS)));
                    return;
                }

                res.setStatusCode(200);

                //返回值可能是json或者简单的value
                List items = new ArrayList();
                items.add(v);
                res.end(mapper.writeValueAsString(new HttpResponse(200, "", items)));
            }
            catch (Exception e) {
                LOG.warn("maps get by key failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        //batch interface /maps/xx?key=11,22,33
        router.get("/maps/:map").handler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            MapEngine me = ms.getEngine(req.getParam("map"));
            res.putHeader("content-type", "application/json");
            String keys = req.getParam("key");
            String wt = req.getParam("wt");

            try {
                if(keys == null) {
                    res.setStatusCode(404);

                    res.end(mapper.writeValueAsString(new HttpResponse(404, "map or key not found", ITEMS)));
                    return;
                }
                String[] kk = keys.split(",");
                List ll = me.getFsm().getValues(kk);

                if(wt != null && wt.equals("map")) {
                    Map m = new HashMap();
                    int i = 0;
                    for(Object o : ll) {
                        m.put(kk[i++], o);
                    }
                    res.end(mapper.writeValueAsString(new HttpMapResponse(200, "", m)));
                    return;
                }

                res.end(mapper.writeValueAsString(new HttpResponse(200, "", ll)));
            }
            catch (Exception e) {
                LOG.warn("maps get by keys failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        //从集群中读取map值，保证线性一致，性能低
        router.get("/cluster/maps/:map/:key").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            res.putHeader("content-type", "application/json");

            try {
                Object rep = mc.get(req.getParam("map"), req.getParam("key"));
                if(rep == null) {
                    res.setStatusCode(404);

                    res.end(mapper.writeValueAsString(new HttpResponse(404, "map or key not found", ITEMS)));
                    return;
                }

                res.setStatusCode(200);

                //返回值可能是json或者简单的value
                List items = new ArrayList();
                items.add(rep);
                res.end(mapper.writeValueAsString(new HttpResponse(200, "", items)));
            }
            catch (Exception e) {
                LOG.warn("maps get by key failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        //cluster batch interface /cluster/maps/xx?key=11,22,33
        router.get("/cluster/maps/:map").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            res.putHeader("content-type", "application/json");

            String keys = req.getParam("key");
            String wt = req.getParam("wt");

            try {
                if(keys == null) {
                    res.setStatusCode(404);

                    res.end(mapper.writeValueAsString(new HttpResponse(404, "map or key not found", ITEMS)));
                    return;
                }

                String[] kk = keys.split(",");
                List list = mc.getMulti(req.getParam("map"), Arrays.asList(kk));

                if(wt != null && wt.equals("map")) {
                    Map m = new HashMap();
                    int i = 0;
                    for(Object o : list) {
                        m.put(kk[i++], o);
                    }
                    res.end(mapper.writeValueAsString(new HttpMapResponse(200, "", m)));
                    return;
                }

                res.end(mapper.writeValueAsString(new HttpResponse(200, "", list)));
            }
            catch (Exception e) {
                LOG.warn("maps get by keys failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        //从集群中删除一个值
        router.delete("/cluster/maps/:map/:key").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            res.putHeader("content-type", "application/json");
            try {
                Object rep = mc.addUpdate(req.getParam("map"), req.getParam("key"), null);

                if(rep == null) {
                    res.setStatusCode(500);
                    res.end(mapper.writeValueAsString(new HttpResponse(500, "update maps failed", ITEMS)));
                    return;
                }

                res.setStatusCode(200);

                List items = new ArrayList();
                items.add(rep);
                res.end(mapper.writeValueAsString(new HttpResponse(200, "", items)));
            }
            catch (Exception e) {
                //e.printStackTrace();
                LOG.warn("cluster maps update by key failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        //cluster batch interface /cluster/maps/xx?key=11,22,33
        router.delete("/cluster/maps/:map").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            res.putHeader("content-type", "application/json");

            String keys = req.getParam("key");

            try {
                if(keys == null) {
                    res.setStatusCode(404);

                    res.end(mapper.writeValueAsString(new HttpResponse(404, "map or key not found", ITEMS)));
                    return;
                }

                List list = mc.addUpdateMulti(req.getParam("map"), Arrays.stream(keys.split(",")).map(x -> new MapServer.KeyValue(x, null)).collect(Collectors.toList()));
                res.end(mapper.writeValueAsString(new HttpResponse(200, "", list)));
            }
            catch (Exception e) {
                LOG.warn("maps delete by keys failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });


        router.route().handler(BodyHandler.create().setBodyLimit(config.getHttp().getPostLimit()));
        //向集群写入一个值，添加或者更新
        router.post("/cluster/maps/:map/:key").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            res.putHeader("content-type", "application/json");
            try {
                String item = routingContext.getBodyAsString();
                Object rep = mc.addUpdate(req.getParam("map"), req.getParam("key"), item);

                if(rep == null) {
                    res.setStatusCode(500);
                    res.end(mapper.writeValueAsString(new HttpResponse(500, "update maps failed", ITEMS)));
                    return;
                }

                res.setStatusCode(200);

                List items = new ArrayList();
                items.add(rep);
                res.end(mapper.writeValueAsString(new HttpResponse(200, "", items)));
            }
            catch (Exception e) {
                //e.printStackTrace();
                LOG.warn("cluster maps update by key failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        //向集群写入多个值，添加/更新/删除，如果值为null，代表删除
        //[{"key":"xxx","value":{"tenthDownPayWz":13107,"tenthMonthlyWz":17476,"tenthDownPayXw":21845,"tenthMonthlyXw":26214,"thirdDownPay":4369,"thirdMonthly":8738}},{"key":"ggg","value":null}]
        router.post("/cluster/maps/:map").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            res.putHeader("content-type", "application/json");
            try {
                String item = routingContext.getBodyAsString();

                List<KeyValue> list = mapper.readValue(item, new TypeReference<List<KeyValue>>(){});
                List ll = mc.addUpdateMulti(req.getParam("map"), list);

                res.setStatusCode(200);
                res.end(mapper.writeValueAsString(new HttpResponse(200, "", ll)));
            }
            catch (Exception e) {
                //e.printStackTrace();
                LOG.warn("cluster maps update by json failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        /*
            集群管理api，用来进行集群节点和配置的查询，管理
        */
        router.get("/cluster/raft/:group/leader").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            res.putHeader("content-type", "application/json");

            try {
                String s = mc.getLeader(req.getParam("group"));
                List items = new ArrayList();
                items.add(s);

                res.setStatusCode(200);
                res.end(mapper.writeValueAsString(new HttpResponse(200, "", items)));
            }
            catch (Exception e) {
                LOG.warn("cluster maps get leader failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        router.get("/cluster/raft/:group/peers").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            res.putHeader("content-type", "application/json");

            try {
                List<String> items = mc.getPeers(req.getParam("group"));

                res.setStatusCode(200);
                res.end(mapper.writeValueAsString(new HttpResponse(200, "", items)));
            }
            catch (Exception e) {
                LOG.warn("cluster maps get leader failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        router.post("/cluster/raft/:group/peers").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            res.putHeader("content-type", "application/json");

            try {
                String item = routingContext.getBodyAsString();
                List<String> list = mapper.readValue(item, new TypeReference<List<String>>(){});

                List items = list.stream().map(
                        peer -> mc.addPeer(req.getParam("group"), peer)
                ).collect(Collectors.toList());

                res.setStatusCode(200);
                res.end(mapper.writeValueAsString(new HttpResponse(200, "", items)));
            }
            catch (Exception e) {
                LOG.warn("cluster maps get leader failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        //TODO: put操作将覆盖本节点的peer配置，除非多数节点永久失效，否则不要使用此功能
        router.put("/cluster/raft/:group/peers").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            res.putHeader("content-type", "application/json");

            try {
                String item = routingContext.getBodyAsString();
                List<String> list = mapper.readValue(item, new TypeReference<List<String>>(){});

                String ret = mc.resetPeer(req.getParam("group"), list);

                if(ret.equals("ok")) {
                    res.setStatusCode(200);
                    res.end(mapper.writeValueAsString(new HttpResponse(200, ret, ITEMS)));
                }
                else {
                    res.setStatusCode(500);
                    res.end(mapper.writeValueAsString(new HttpResponse(500, ret, ITEMS)));
                }
            }
            catch (Exception e) {
                LOG.warn("cluster maps get leader failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        // /cluster/raft/xx/peers?id=1.1.1.1:8888,1.1.1.2:8888
        router.delete("/cluster/raft/:group/peers").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            res.putHeader("content-type", "application/json");

            try {
                String ids = req.getParam("id");
                res.setStatusCode(200);

                List list = Arrays.stream(ids.split(",")).map(peer ->
                           mc.removePeer(req.getParam("group"), peer)
                        ).collect(Collectors.toList());
                res.end(mapper.writeValueAsString(new HttpResponse(200, "", list)));
            }
            catch (Exception e) {
                LOG.warn("cluster maps get leader failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        router.get("/cluster/raft/:group/metrics").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            MapEngine me = ms.getEngine(req.getParam("group"));
            res.putHeader("content-type", "application/json");

            try {
                if (me == null ) {
                    res.setStatusCode(404);

                    res.end(mapper.writeValueAsString(new HttpResponse(404, "map or key not found", ITEMS)));
                    return;
                }
                res.setStatusCode(200);
                List items = new ArrayList();
                items.addAll(me.getMetricKeys());

                res.end(mapper.writeValueAsString(new HttpResponse(200, "", items)));
            }
            catch (Exception e) {
                LOG.warn("cluster maps get leader failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        router.get("/cluster/raft/:group/metrics/:key").blockingHandler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            MapEngine me = ms.getEngine(req.getParam("group"));
            res.putHeader("content-type", "application/json");


            try {
                if (me == null ) {
                    res.setStatusCode(404);

                    res.end(mapper.writeValueAsString(new HttpResponse(404, "map or key not found", ITEMS)));
                    return;
                }
                res.setStatusCode(200);
                List items = new ArrayList();
                Metric metric = me.getMetric(req.getParam("key"));
                items.add(metric);

                res.end(mapper.writeValueAsString(new HttpResponse(200, "", items)));
            }
            catch (Exception e) {
                LOG.warn("cluster maps get leader failed", e);
                throw new IllegalArgumentException(e.getCause().toString());
            }
        });

        router.route().handler(routingContext -> {
            HttpServerRequest req = routingContext.request();
            HttpServerResponse res = routingContext.response();

            res.putHeader("content-type", "application/json");
            res.setStatusCode(404);

            String s = "{\"status\":404,\"msg\":\"path not found\",\"items\":[]}";

            res.end(s);
        });


        // 异常处理流程
        router.route().failureHandler(failureRoutingContext -> {
            HttpServerResponse response = failureRoutingContext.response();
            response.putHeader("content-type", "application/json");

            response.setStatusCode(400);
            String s = "{\"status\":400,\"msg\":\"bad request\",\"items\":[]}";
            try {
                s = mapper.writeValueAsString(new HttpResponse(400, failureRoutingContext.failure().getMessage(), ITEMS));
            }
            catch (Exception e){}

            response.end(s);
        });

        server.requestHandler(router).listen(config.getHttp().getPort(), config.getHttp().getIp()!=null?config.getHttp().getIp():"0.0.0.0");
    }


    public static class VarConstructor extends Constructor {
        final static Pattern p = Pattern.compile("\\$\\{([^}^{]+)}");

        public VarConstructor(Class<? extends Object> theRoot) {
            super(new TypeDescription(theRoot));
            this.yamlConstructors.put(new Tag("!var"), new ConstructVar());
        }

        private class ConstructVar extends AbstractConstruct {


            public Object construct(org.yaml.snakeyaml.nodes.Node node) {
                String val = (String) constructScalar((ScalarNode) node);
                StringBuilder sb = new StringBuilder();

                Matcher m = p.matcher(val);
                String value;
                int begin = 0;

                while(m.find()) {
                    //拼接上一个段落
                    sb.append(val, begin, m.start());
                    //获取环境变量
                    String key = m.group(1);
                    value = System.getenv(key);
                    sb.append(value);
                    begin = m.end();
                }

                if(begin < val.length()) {
                    sb.append(val, begin, val.length());
                }

                return sb.toString();
            }
        }
    }

    public static void main(String[] args) {
        if(args.length < 1) {
            System.out.println("No configuration file specified!");
            System.exit(-1);
        }
        SimpleModule module = new SimpleModule();
        module.addDeserializer(KeyValue.class, new KeyValueDeserializer());
        mapper.registerModule(module);

        ColumnFamilyOptions cfOptions = StorageOptionsFactory.getDefaultRocksDBColumnFamilyOptions();
        //如果显式设置了-DNO_COMPRESSION，不压缩
        if(System.getProperty("NO_COMPRESSION") != null) {
            cfOptions.setCompressionType(CompressionType.NO_COMPRESSION);
            StorageOptionsFactory.registerRocksDBColumnFamilyOptions(RocksDBLogStorage.class, cfOptions);
        }

        Yaml yaml = new Yaml(new VarConstructor(ApplicationConfig.class));
        Pattern p = Pattern.compile(".*\\$\\{([^}^{]+)}.*");
        yaml.addImplicitResolver(new Tag("!var"), p, null);

        try {
            InputStream inputStream = new FileInputStream(args[0]);
            ApplicationConfig rc = yaml.load(inputStream);
            inputStream.close();

            MapServer ms = new MapServer(rc);
            System.out.println("Started Maps Server.");
            initRest(rc, ms);
            System.out.println("Start Http Server.");
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
