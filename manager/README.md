### kv接口示例，以testp为例：
1. 访问本地map数据

   http://127.0.0.1:9090/maps/testp?key=98300,196600
   
   * items返回map格式
   
   http://127.0.0.1:9090/maps/testp?key=98300,196600&wt=map
   
   ```json
   {
    "status": 200,
    "msg": "",
    "items": [
        {
            "downPay": 29490,
            "monthly": 2426.88
        },
        {
            "downPay": 58980,
            "monthly": 4853.75
        }
      ]
    }
    ```
2. 提交数据，当value为null时代表删除
   
   http --timeout 60 -h  --json post http://127.0.0.1:9090/cluster/maps/testp < ./finprice.txt
   
   数据格式：
```json
[
    {
        "value": {
            "monthly": 20224.9,
            "tenthDownPayWz": 81920
        },
        "key": 819200
    },
    {
        "value": {
            "monthly": 17797.99,
            "tenthDownPayWz": 72090
        },
        "key": 720900
    },
    {
        "value": null,
        "key": 1111
    }
]   
  
```

### 管理接口示例

1. 获得某个raft group的节点列表

   http://127.0.0.1:9090/cluster/raft/testp/peers
   
   ```json
   {
    "status": 200,
    "msg": "",
    "items": [
        "127.0.0.1:9091",
        "127.0.0.1:9092"
    ]
    }
   
   ```

2. 获得某个raft group的leader
   
   http://127.0.0.1:9090/cluster/raft/testp/leader

   ```json
   {
    "status": 200,
    "msg": "",
    "items": [
        "127.0.0.1:9091"
    ]
    }
   
   ```

3. 获得某个raft group的统计项列表
   
   http://127.0.0.1:9090/cluster/raft/testp/metrics

```json
   {
    "status": 200,
    "msg": "",
    "items": [
        "fsm-snapshot-load",
        "pre-vote",
        "replicator-testp/127.0.0.1:9091.install-snapshot-times",
        "request-vote",
        "append-logs-bytes",
        "handle-append-entries-count",
        "replicator-testp/127.0.0.1:9091.heartbeat-times",
        "append-logs",
        "replicate-entries-count",
        "truncate-log-prefix",
        "replicator-testp/127.0.0.1:9091.log-lags",
        "raft-utils-closure-thread-pool.completed",
        "raft-utils-closure-thread-pool.pool-size",
        "raft-rpc-client-thread-pool.pool-size",
        "handle-append-entries",
        "raft-rpc-client-thread-pool.completed",
        "replicator-testp/127.0.0.1:9091.next-index",
        "fsm-stop-following",
        "raft-rpc-client-thread-pool.active",
        "raft-utils-closure-thread-pool.queued",
        "append-logs-count",
        "replicator-testp/127.0.0.1:9091.append-entries-times",
        "save-raft-meta",
        "replicate-entries-bytes",
        "replicate-entries",
        "raft-rpc-client-thread-pool.queued",
        "raft-utils-closure-thread-pool.active",
        "fsm-start-following",
        "replicate-inflights-count",
        "fsm-commit"
    ]
}
```

4. 获得某个raft group的某个统计项的详细信息
   
   比如从leader获取某个副本当前log复制的进度，可以用来定期监控副本知否和leader一致

   http://127.0.0.1:9090/cluster/raft/testp/metrics/replicator-testp%2F127.0.0.1:9091.log-lags

   ```json
   {
    "status": 200,
    "msg": "",
    "items": [
        {
            "value": 0
        }
    ]
   }
   ```
   
### 配置指南
##### 所有的配置项都支持变量替换，形如${FOO}，其中FOO代表环境变量

```yaml
# http服务器配置项
http:
  port: 9090 # http服务端口
  ip: 0.0.0.0 # http服务ip
  post_limit: 100M # 最大post文件大小，支持K,M后缀
  max_workers: 20 # http服务器工作线程，一般用来处理post任务，根据post的大小和频率设置
# raft协议通用配置项
raft:
  data_dir: ./raft/ # raft数据存放的路径，主要保存raft元数据，日志和快照
  election_timeout: 5 # 秒，follower超过此值后没收到heartbeat就会发起选举
  local_addr: ${RAFT_IP}:9091 # raft绑定的地址，作为身份标识，在集群中必须唯一 
  rpc_timeout: 5 # 秒，rpc调用的超时设置，超过后抛出异常 
  peers: ['127.0.0.1:9091'] # 集群节点列表，仅用于manager第一次启动时使用，后续可以动态增添节点
# 各个map配置项
maps:
  - name: test # map名字，需要唯一
    location: ./test.dat # map文件的路径，一般放在当前路径下即可，方便统一管理
    max_size: 10000 # map最大的entry数目，务必设置足够大，避免满了后无法添加的麻烦
    key_class: java.lang.String # map的key类型，可以为java基础类型Integer,Long,String等或者自定义类型
    val_class: java.lang.String # map的value类型，可以为java基础类型Integer,Long,String等或者自定义类型
    snapshot_interval: 3600 # 快照时间间隔
    rpc_batch_size: 10 # rpc批量调用大小，rpc批量读取和写入能够显著提高效率，一般设置成10-100
    key_size: 10 # 可选，key的平均大小，用于指导map文件的初始化，如果不设置会尝试调用类型的avgSize方法，定长基础类型无需设置
    val_size: 40 # 可选，value的平均大小，用于指导map文件的初始化，如果不设置会尝试调用类型的avgSize方法，定长基础类型无需设置
    peers: ['127.0.0.1:9091'] # 可选，map单独设置的节点列表，如果map的集群节点和全局不同，可以在这里设置
    ignore_update_error: false # 可选，是否忽略更新错误，默认为true，如果设置为false，更新错误后将挂起状态机，需要手动处理，适合对更新安全性要求极高的场景
  - name: testp
    location: ./testp.dat
    max_size: 50000
    key_class: java.lang.Integer
    val_class: org.shmmap.model.TestBytes
    rpc_batch_size: 100
    snapshot_interval: 60 # in second
```
