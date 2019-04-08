#### 简介：
1. manager为共享map管理程序，支持集群复制，提供http接口可以进行map的增删查改操作，依赖业务插件进行序列化的操作
2. testp为演示用的业务插件，可以访问manager管理的map

#### 主要特点：
1. 基于共享内存的ChronicleMap，可以实现微秒级的查询和写入，同时多个进程可以共享同一个map，节约了内存
2. 基于jraft实现的kv复制和map快照功能，集群更新可以达到秒级延时，支持动态增删节点
3. 支持批处理，优化了错误恢复的处理，manager恢复后使用者可以不重启热更新map


#### 运维指南：
1. 每台机器部署一个manager，作为所有map的唯一管理者，可以部署多个使用程序，比如solr/es插件，一般的java程序等，这些程序对map必须是只读的
2. map的大小一定要设置的足够充足，chronicleMap没有自动扩容功能，如果map写满了，基本上这个map也就废了
3. 当manager程序重启后，它会加载快照并重放日志来进行恢复操作，这时使用相应map的程序应当重新加载或者重启(比如testp实现了reload接口，无需重启)
4. manager支持动态增删节点，相应的节点配置好后，在原集群调用相应接口即可实现


#### 插件开发指南
1. 什么时候需要开发插件？k/v为自定义类型时需要，如果kv都是基本数据类型，不需要开发插件
2. 插件开发需要依赖common模块的类：
   
   BytesSerializer：必须实现的序列化类，map底层都是Bytes类型，此类用于map的定义类型和Bytes的互转。其中的read,write,avgSize方法都需要实现

   BytesConverter：可选实现的Bytes转化类，适合读写类型非对称的情况，比如写入使用自定义对象类型，而读取只需要从Bytes里读取一个int就行，
                   这种情况下map的定义类型是Bytes，写入前先将自定义类型转化成Bytes，读取则直接从Bytes里手动解析出值。想实现这种优化的类型就
                   需要实现这个接口。
                   
   TlvSerializer：TLV编码的序列化基类，可用于对简单对象的序列化，用户可以选择使用或者使用更复杂的序列化方式（比如PB,Kryo等）
   
   MapFileUtils：map文件读取工具类，因为manager对所有map文件加了时间戳后缀，所有读取map的程序都需要使用此类的getLatestFile方法来获取map文件
   
3. 自定义类型强制约定：必须实现Serializable接口，必须包含名字为Serializer的静态内部类（实现序列化），Serializer里必须定义名为INSTANCE的静态公有单例
4. 一般可以开发单独的插件，也可以将插件需要的类集成到应用jar包里，建议使用后面的方式，这样避免应用修改和manager插件不一致的情况发生
