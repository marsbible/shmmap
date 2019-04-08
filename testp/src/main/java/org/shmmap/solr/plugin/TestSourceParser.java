package org.shmmap.solr.plugin;

import org.shmmap.common.MapFileUtils;
import org.shmmap.model.TestBytes;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.map.ChronicleMap;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class TestSourceParser extends ValueSourceParser {
    class Property {
        private int size;
        private String data;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    //名字到map的映射表
    protected AtomicReference<ChronicleMap<Integer,Bytes>> map = new AtomicReference<>();

    public Map<Integer, Bytes> getMap() {
        return map.get();
    }

    private Property property = new Property();

    @Override
    public synchronized void init(NamedList namedList) {
        Iterator<Map.Entry> it = namedList.iterator();
        //支持data,size两个参数

        while(it.hasNext()) {
            Map.Entry e = it.next();

            if(e.getKey().equals("data")) {
                this.property.setData(e.getValue().toString());
            }
            else if(e.getKey().equals("size")) {
                this.property.setSize((Integer)e.getValue());
            }
        }

        System.out.println("TestSourceParser init: " + namedList.toString());

        try {
            File f =  MapFileUtils.getLatestFile(this.property.getData());
            if(f == null) {
                throw new IllegalStateException("No map file " + this.property.getData() + " existed, critical error ...");
            }
            ChronicleMap tmp = ChronicleMap
                    .of(Integer.class, Bytes.class)
                    .entries(this.property.getSize())
                    .averageValueSize(TestBytes.Serializer.INSTANCE.avgSize())
                    .valueMarshaller(TestBytes.Serializer.INSTANCE)
                    .createPersistedTo(f);

            map.set(tmp);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            //加载失败，致命错误，退出整个solr进程
            System.exit(-1);
        }
    }

    public synchronized int reload() {
        try {
            File f =  MapFileUtils.getLatestFile(this.property.getData());
            if(f == null) {
                throw new IllegalStateException("No map file " + this.property.getData() + " existed, critical error ...");
            }

            ChronicleMap tmp = ChronicleMap
                    .of(Integer.class, Bytes.class)
                    .entries(this.property.getSize())
                    .averageValueSize(TestBytes.Serializer.INSTANCE.avgSize())
                    .valueMarshaller(TestBytes.Serializer.INSTANCE)
                    .createPersistedTo(f);

            ChronicleMap old = map.get();
            map.set(tmp);

            if(old != null) old.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
            //加载失败
            return -1;
        }

        return 0;
    }

    public ValueSource parse(FunctionQParser fp) throws SyntaxError {
        ValueSource source = fp.parseValueSource();
        String type = fp.parseArg();
        int plan = fp.parseInt();

        if(type.equals("downpay")) {
            return new TestDownPaySource(map, source, plan);
        }
        else if(type.equals("monthly")) {
            return new TestMonthlySource(map, source, plan);
        }
        else {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                    "One or more inputs missing for finPrice function");
        }
    }
}
