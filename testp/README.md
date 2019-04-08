1. 查询语句调用方法类似finPrice(tp_201,shoufu,1),第一个参数是展板价字段，第二个是返回类型(shoufu或者yuegong)，
   第三个字段是金融方案(1代表微众一成方案，2代表新网一成方案，4代表三成方案，可以采用按位或的方式指定多个方案，返回顺序是微众->新网->三成)
2. solrconfig.xml需要增加如下的结构，data参数代表要访问的数据文件，size代表最大kv数量
    ```xml
   <valueSourceParser name="finPrice" class="com.xin.shmmap.solr.plugin.TestSourceParser" >
        <str name="data">${solr.solr.home}/finprice.dat</str>
        <int name="size">50000</int>
   </valueSourceParser>
    ```
3. 写入数据使用类似下列方法：
   ```bash
   # 最后的参数key1 value1 key2 value2 ...
   java -jar shmmap.jar Write Integer Integer 50000 E:\solr-5.2.1\server\node1\test.dat 12 23 33 44 
   
   # 文件里每行用tab分割kv，key\tvalue
   java -jar shmmap.jar Write Integer Integer 50000 E:\solr-5.2.1\server\node1\test.dat -f data.txt
   ```
   
   读取数据使用类似下列方法：
   ```bash
   # 最后的参数key1 key2 key3 ...
   java -jar shmmap.jar Read Integer Integer 50000 E:\solr-5.2.1\server\node1\test.dat 12 33
   
   # 文件里每行一个key
   java -jar shmmap.jar Read Integer Integer 50000 E:\solr-5.2.1\server\node1\test.dat -f data.txt
   ```
   
   对于FinPrice类型，文件中的格式需要为json类型（内部用的二进制编码）：
   ```json
   {
      "tenthDownPayWz": 13107,
      "tenthMonthlyWz": 17476,
      "tenthDownPayXw": 21845,
      "tenthMonthlyXw": 26214,
      "downPay": 4369,
      "monthly": 8738
    }
    ```

4. 重新打开map文件接口，当manager节点重启后，需要调用这个接口确保solr能够及时使用最新的map文件
    
   http://localhost:8983/solr/car_binlog/reloadShmmap?name=finPrice

