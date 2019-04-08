package org.shmmap;


import org.shmmap.common.BytesConverter;
import org.shmmap.common.BytesSerializer;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(description = "读取和写入chronicle map的命令行程序.",
        name = "shmmap", mixinStandardHelpOptions = true, version = "shmmap 1.0")
public class ShmMapCmd implements Callable<Void> {

    enum Action { Read, Write }

    //只支持int,float,double,string这几种内置类型和TlvSerializer/TlvSerializerBytes的子类型
    enum Type {
        Integer("java.lang.Integer"),
        Long("java.lang.Long"),
        Float("java.lang.Float"),
        Double("java.lang.Double"),
        String("java.lang.String"),
        //Test("com.xin.solr.plugin.shmmap.Test");
        FinPrice("com.xin.shmmap.model.TestBytes"); //FinPrice读取写入使用Bytes类型，提升读取性能

        private String value;

        // getter method
        public String getValue()
        {
            return this.value;
        }

        private Type(String value)
        {
            this.value = value;
        }
    }

    @Parameters(index = "0", description = "动作: ${COMPLETION-CANDIDATES}.")
    Action action = null;

    @Parameters(index = "1", description = "key的类型: ${COMPLETION-CANDIDATES}.")
    private Type key;

    @Parameters(index = "2", description = "value的类型: ${COMPLETION-CANDIDATES}.")
    private Type value;

    @Parameters(index = "3", description = "entry的最大大小.")
    private int size;

    @Parameters(index = "4", description = "需要读取/写入的chronicle map文件路径.")
    private File cmap;

    @Parameters(index="5", arity = "0..*", description = "读取指定key值，写入指定key,value值")
    private String[] items = new String[0];

    @Option(names = { "-f", "--file" }, description = "直接从数据文件读取key/value")
    private File data;

    @Option(names = { "-k", "--keySize" }, description = "key的平均大小(字节)，String类型必须设置")
    private int ksize = 0;

    @Option(names = { "-v", "--valSize" }, description = "value的平均大小(字节)，String类型必须设置")
    private int vsize = 0;

    @Override
    public Void call() throws Exception {

        try {
            Class<?> kCls = Class.forName(key.getValue());
            Class<?> vCls = Class.forName(value.getValue());

            boolean bytesConverterK = BytesConverter.class.isAssignableFrom(kCls);
            boolean bytesConverterV = BytesConverter.class.isAssignableFrom(vCls);

            Class<?> kn  = bytesConverterK?Bytes.class:kCls;
            Class<?> vn  = bytesConverterV?Bytes.class:vCls;

            BytesSerializer bytesSerializerK = null;
            BytesSerializer bytesSerializerV = null;

            try {
                Class<?> t = Class.forName(key.getValue()+"$Serializer");
                Field f = t.getDeclaredField("INSTANCE");
                bytesSerializerK = (BytesSerializer) f.get(null);
            }
            catch (Exception e){}

            try {
                Class<?> t = Class.forName(value.getValue()+"$Serializer");
                Field f = t.getDeclaredField("INSTANCE");
                bytesSerializerV = (BytesSerializer) f.get(null);
            }
            catch (Exception e){}

            ChronicleMapBuilder cmb =  ChronicleMap
                    .of(kn, vn)
                    .entries(size);

            if(ksize != 0) {
                cmb = cmb.averageKeySize(ksize);
            }
            else {
                //没有设置ksize，尝试调用avgSize方法
                try {
                    if(bytesSerializerK != null) cmb = cmb.averageKeySize(bytesSerializerK.avgSize());
                }
                catch (Exception e) {}
            }

            if(vsize != 0) {
                cmb = cmb.averageValueSize(vsize);
            }
            else {
                //没有设置valueSize，尝试调用avgSize方法
                try {
                    if(bytesSerializerV != null) cmb = cmb.averageValueSize(bytesSerializerK.avgSize());
                }
                catch (Exception e){}
            }

            if(bytesSerializerK != null) {
                cmb = cmb.keyMarshaller(bytesSerializerK);
            }

            if(bytesSerializerV != null) {
                cmb = cmb.valueMarshaller(bytesSerializerV);
            }

            ChronicleMap cm  = cmb.createPersistedTo(cmap);

            if (action == Action.Read) {
                Constructor cons = kCls.getConstructor(String.class);
                Constructor cons2 = vCls.getConstructor();

                List<String> ret = new ArrayList<>();
                int j = 0;

                for (String s : items) {
                    Object k = cons.newInstance(s);
                    Object v = cm.get(bytesConverterK?((BytesConverter)k).toBytes():k);

                    if(v == null) {
                        ret.add(null);
                    }
                    else {
                        if(bytesConverterV) {
                            Object t = cons2.newInstance();
                            ((BytesConverter)t).fromBytes((Bytes)v);
                            v = t;
                        }

                        ret.add(v.toString());
                    }
                    j++;
                }

                if(data != null) {
                    BufferedReader br = new BufferedReader(new FileReader(data));
                    String s;

                    while((s = br.readLine()) != null) {
                        Object k = cons.newInstance(s);
                        Object v = cm.get(bytesConverterK?((BytesConverter)k).toBytes():k);

                        if(v == null) {
                            ret.add(null);
                        }
                        else {
                            if(bytesConverterV) {
                                Object t = cons2.newInstance();
                                ((BytesConverter)t).fromBytes((Bytes)v);
                                v = t;
                            }

                            ret.add(v.toString());
                        }
                        j++;
                    }

                    br.close();
                }
                System.out.printf("读取 %d key完成.\n", j);
                System.out.println(ret);
            }
            else {
                Constructor cons = kCls.getConstructor(String.class);
                Constructor cons2 = vCls.getConstructor(String.class);
                int j = 0;

                for(int i=0; i<items.length; i+=2) {
                    Object ko = cons.newInstance(items[i]);
                    Object vo = cons2.newInstance(items[i+1]);

                    cm.put(bytesConverterK?((BytesConverter)ko).toBytes():ko, bytesConverterV?((BytesConverter)vo).toBytes():vo);
                    j++;
                }

                if(data != null) {
                    BufferedReader br = new BufferedReader(new FileReader(data));

                    String s;
                    while ((s = br.readLine()) != null) {
                        String[] x = s.split("\t");

                        Object ko = cons.newInstance(x[0]);
                        Object vo = cons2.newInstance(x[1]);

                        cm.put(bytesConverterK?((BytesConverter)ko).toBytes():ko, bytesConverterV?((BytesConverter)vo).toBytes():vo);
                        j++;
                    }

                    br.close();
                }

                System.out.printf("写入 %d kv完成.\n", j);
            }

            cm.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    public static void main(String[] args) throws Exception {
        CommandLine.call(new ShmMapCmd(), args);
    }
}