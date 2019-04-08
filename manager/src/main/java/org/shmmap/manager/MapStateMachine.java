package org.shmmap.manager;

import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import org.shmmap.common.BytesConverter;
import org.shmmap.common.BytesSerializer;
import org.shmmap.common.MapFileUtils;
import org.shmmap.manager.config.MapConfig;
import org.shmmap.manager.rpc.AddUpdateBatchRequest;
import org.shmmap.manager.rpc.AddUpdateRequest;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class MapStateMachine extends StateMachineAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(MapStateMachine.class);

    private MapConfig mapConfig;

    /**
     * chronicle map
    */
    private ChronicleMap map;

    private Class<?> kCls;
    private Class<?> vCls;

    private Constructor keyStringCons;
    private Constructor valStringCons;


    private BytesSerializer bytesSerializerK;
    private BytesSerializer bytesSerializerV;

    private boolean bytesConverterK;
    private boolean bytesConverterV;

    private File dataFile;

    /**
     * Leader term
     */
    private final AtomicLong leaderTerm = new AtomicLong(-1);

    public boolean isLeader() {
        return this.leaderTerm.get() > 0;
    }

    protected void initMap() throws Exception {
        MapConfig mc = this.mapConfig;

        kCls = Class.forName(mc.getKey_class());
        vCls = Class.forName(mc.getVal_class());

        keyStringCons = kCls.getConstructor(String.class);
        valStringCons = vCls.getConstructor(String.class);

        //如果包含toBytes方法，写入map前需要转换成Bytes
        bytesConverterK = BytesConverter.class.isAssignableFrom(kCls);
        bytesConverterV = BytesConverter.class.isAssignableFrom(vCls);

        Class<?> kn  = bytesConverterK?Bytes.class:kCls;
        Class<?> vn  = bytesConverterV?Bytes.class:vCls;

        try {
            Class<?> t = Class.forName(mc.getKey_class()+"$Serializer");
            Field f = t.getDeclaredField("INSTANCE");
            bytesSerializerK = (BytesSerializer) f.get(null);
        }
        catch (Exception e){}

        try {
            Class<?> t = Class.forName(mc.getVal_class()+"$Serializer");
            Field f = t.getDeclaredField("INSTANCE");
            bytesSerializerV = (BytesSerializer) f.get(null);
        }
        catch (Exception e){}

        ChronicleMapBuilder cmb = ChronicleMap
                .of(kn, vn)
                .entries(mc.getMax_size());

        if (mc.getKey_size() != 0) {
            cmb = cmb.averageKeySize(mc.getKey_size());
        } else {
            //没有设置ksize，尝试调用avgSize方法
            try {
                if(bytesSerializerK != null) cmb = cmb.averageKeySize(bytesSerializerK.avgSize());
            }
            catch (Exception e) {}
        }

        if (mc.getVal_size() != 0) {
            cmb = cmb.averageValueSize(mc.getVal_size());
        } else {
            //没有设置valueSize，尝试调用avgSize方法
            try {
                if(bytesSerializerV != null) cmb = cmb.averageValueSize(bytesSerializerV.avgSize());
            }
            catch (Exception e){}
        }

        if(bytesSerializerK != null) {
            cmb = cmb.keyMarshaller(bytesSerializerK);
        }

        if(bytesSerializerV != null) {
            cmb = cmb.valueMarshaller(bytesSerializerV);
        }

        if(dataFile == null)  dataFile = MapFileUtils.getAvailableFile(mc.getLocation());

        map = cmb.createPersistedTo(dataFile);
    }

    protected void closeMap() {
        if(map != null) map.close();
        map = null;
    }


    public MapStateMachine(MapConfig mc) {
        this.mapConfig = mc;
        try {
            initMap();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns current value.
     */
    public Object getValue(String key) {
        if(key == null) return null;

        try {
            Object ko = keyStringCons.newInstance(key);
            ko = bytesConverterK ? ((BytesConverter) ko).toBytes() : ko;
            Object r = this.map.get(ko);

            if (r == null) {
                return null;
            }

            if (bytesConverterV) {
                Object t = vCls.newInstance();
                ((BytesConverter) t).fromBytes((Bytes) r);
                return t;
            } else {
                return r;
            }
        }
        catch (Exception e) {
            return null;
        }
    }

    public List getValues(String[] keys) {
        List<Object> ret = new ArrayList<>();
        for(String key: keys) {
            ret.add(getValue(key));
        }

        return ret;
    }

    private void mapOp(String key, String value) throws Exception {
        Object prev;
        Object ko = keyStringCons.newInstance(key);
        ko = bytesConverterK? ((BytesConverter)ko).toBytes():ko;

        //删除
        if(value == null) {
            prev = this.map.remove(ko);
        }
        else {
            Object vo = valStringCons.newInstance(value);
            prev = this.map.put(ko, bytesConverterV? ((BytesConverter)vo).toBytes():vo);
        }

        LOG.info("update key={} new_value={}", ko, value);
    }

    @Override
    public void onApply(final Iterator iter) {
        //中途的任何失败和异常都直接返回，raft后续会作为致命错误处理
        while (iter.hasNext()) {
            String key;
            String value;

            Closure closure = null;
            Object request = null;

            if (iter.done() != null) {
                // This task is applied by this node, get value from closure to avoid additional parsing.
                closure = iter.done();
                if(closure instanceof AddUpdateClosure) {
                    request = ((AddUpdateClosure)closure).getRequest();
                }
                else {
                    request = ((AddUpdateBatchClosure)closure).getRequest();
                }
            } else {
                // Have to parse AddUpdateRequest from this user log.
                final ByteBuffer data = iter.getData();

                try {
                    request = SerializerManager.getSerializer(SerializerManager.Hessian2)
                            .deserialize(data.array(), AddUpdateRequest.class.getName());
                } catch (final CodecException e) {
                    try {
                        request = SerializerManager.getSerializer(SerializerManager.Hessian2)
                                .deserialize(data.array(), AddUpdateBatchRequest.class.getName());
                    }
                    catch (final CodecException e2) {
                        LOG.error("Fail to decode AddUpdateRequest", e);
                        if(mapConfig.isIgnore_update_error()) {
                            iter.next();
                            continue;
                        }
                        else {
                            throw new IllegalArgumentException("Failed to decode AddUpdateRequest");
                        }
                    }
                }
            }

            if(request == null) {
                iter.next();
                LOG.error("apply failed, request is null");
                continue;
            }

            if(request instanceof AddUpdateRequest) {
                key = ((AddUpdateRequest)request).getKey();
                value = ((AddUpdateRequest)request).getValue();

                if(key == null) {
                    iter.next();
                    LOG.error("apply failed, key is null");
                    continue;
                }

                String rv = "ok";
                try {
                    mapOp(key, value);
                }
                catch (Exception e) {
                    //e.printStackTrace();
                    rv = e.getMessage();
                    LOG.error("Apply failed due to exception", e);
                    if(!mapConfig.isIgnore_update_error()) {
                        throw new IllegalStateException("Apply failed due to exception");
                    }
                }

                if (closure != null) {
                    if(rv.equals("ok")) {
                        ((AddUpdateClosure) closure).getResponse().setValue(rv);
                        ((AddUpdateClosure) closure).getResponse().setSuccess(true);
                        closure.run(Status.OK());
                    }
                    else {
                        ((AddUpdateClosure) closure).getResponse().setValue(rv);
                        ((AddUpdateClosure) closure).getResponse().setSuccess(false);

                        Status s = new Status();
                        s.setCode(400);
                        s.setError(400, rv);
                        closure.run(s);
                    }
                }
            }
            else {
                AddUpdateBatchRequest ar = (AddUpdateBatchRequest)request;
                Object[] values = new Object[ar.getRequests().length];
                int i = 0;

                for(AddUpdateBatchRequest.Request r: ar.getRequests()) {
                    key = r.getKey();
                    value = r.getValue();

                    if(key == null) {
                        LOG.error("apply failed, key is null");
                        values[i++] = null;
                        continue;
                    }

                    try {
                        mapOp(key, value);
                        values[i++] = "ok";
                    }
                    catch (Exception e) {
                        //e.printStackTrace();
                        values[i++] = e.getMessage();
                        LOG.error("Apply failed due to exception", e);
                        if(!mapConfig.isIgnore_update_error()) {
                            throw new IllegalStateException("Apply failed due to exception");
                        }
                    }
                }

                if (closure != null) {
                    ((AddUpdateBatchClosure)closure).getResponse().setValues(values);
                    ((AddUpdateBatchClosure)closure).getResponse().setSuccess(true);
                    closure.run(Status.OK());
                }
            }

            iter.next();
        }
    }

    @Override
    public void onSnapshotSave(final SnapshotWriter writer, final Closure done) {
        //拷贝当前文件成snapshot
        try {
            FileUtils.copyFile(dataFile, new File(writer.getPath() + File.separator + "data"));

            if (writer.addFile("data")) {
                done.run(Status.OK());
            } else {
                done.run(new Status(RaftError.EIO, "Fail to add file to writer"));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            done.run(new Status(RaftError.EIO, "Fail to save map snapshot %s", writer.getPath() + File.separator + "data"));
        }
    }

    @Override
    public void onError(final RaftException e) {
        LOG.error("Raft error: %s", e, e);
    }

    @Override
    public boolean onSnapshotLoad(final SnapshotReader reader) {
        if (isLeader()) {
            LOG.warn("Leader is not supposed to load snapshot");
            return false;
        }
        if (reader.getFileMeta("data") == null) {
            LOG.error("Fail to find data file in {}", reader.getPath());
            return false;
        }
        String fname = mapConfig.getLocation();
        //删除初始化文件
        if(dataFile != null) {
            closeMap();
            dataFile.delete();
            dataFile = null;
        }

        File tmpData = MapFileUtils.getAvailableFile(fname);

        try {
            File tmp = new File(fname + "." + System.currentTimeMillis());
            //先拷贝再move，保证文件访问的一致性
            FileUtils.copyFile(new File(reader.getPath() + File.separator + "data"), tmp);
            FileUtils.moveFile(tmp, tmpData);
            dataFile = tmpData;
            closeMap();
            initMap();
        }
        catch (Exception e) {
            LOG.error("Failed to copy&init map {}: {}", dataFile.getPath(), e.getMessage());
            //e.printStackTrace();
            return false;
        }

        return true;
    }

    @Override
    public void onLeaderStart(final long term) {
        this.leaderTerm.set(term);
        super.onLeaderStart(term);

    }

    @Override
    public void onLeaderStop(final Status status) {
        this.leaderTerm.set(-1);
        super.onLeaderStop(status);
    }
}
