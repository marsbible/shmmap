package org.shmmap.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.shmmap.common.BytesSerializer;
import org.shmmap.common.TlvSerializer;
import org.shmmap.common.BytesConverter;
import net.openhft.chronicle.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serializable;


public class TestBytes implements Serializable, BytesConverter
{
    private static final long serialVersionUID = 5764780256367936936L;

    public static class Serializer extends TlvSerializer<TestBytes,Bytes> {
        public static final Serializer INSTANCE = new Serializer();

        @NotNull
        @Override
        public Bytes read(Bytes in, @Nullable Bytes using) {
            //为了优化读取性能不返回FinPrice对象，使用者按需直接从Bytes里读取
            return in;
        }

        @Override
        public void write(Bytes out, @NotNull Bytes toWrite) {
            try {
                out.write(toWrite);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        @NotNull
        public BytesSerializer readResolve() {
            return INSTANCE;
        }


        @Override
        public int avgSize() {
            //2 field + 2 bytes + 2 bytes
            return 2*6+2+2;
        }

        @Override
        protected void writeBody(Bytes out, TestBytes o) {
            out.writeInt(o.downPay);
            out.writeFloat(o.monthly);
        }

        @Override
        protected void readBody(Bytes in, TestBytes o) {
            o.downPay = in.readInt();
            o.monthly = in.readFloat();
        }

        //从字节流直接解析出需要的值，避免对象分配，只能读取一次
        public int parseDownPay(Bytes in, int plan) throws IOException {
            int len = readHead(in);

            return in.readInt();
        }

        //从字节流直接解析出需要的值，避免对象分配，只能读取一次
        public float parseMonthly(Bytes in, int plan) throws IOException {
            int len = readHead(in);

            in.readInt();

            return in.readFloat();
        }
    }

    private static ObjectMapper mapper = new ObjectMapper();

    private int downPay;
    private float monthly;

    public static final TestBytes INSTANCE = new TestBytes();

    public TestBytes() {}

    public TestBytes(String json) throws IOException {
        mapper.readerForUpdating(this).readValue(json);
    }

    @Override
    public String toString() {
        try {
            return mapper.writer().writeValueAsString(this);
        }
        catch (Exception e) {
            return "{}";
        }
    }

    public int getDownPay() {
        return downPay;
    }

    public void setDownPay(int downPay) {
        this.downPay = downPay;
    }

    public float getMonthly() {
        return monthly;
    }

    public void setMonthly(float monthly) {
        this.monthly = monthly;
    }

    @Override
    public Bytes toBytes() throws IOException{
        Bytes out = Bytes.elasticByteBuffer(128);
        Serializer.INSTANCE.writeTlv(out, this);
        return out;
    }

    @Override
    public void fromBytes(Bytes in) throws IOException {
        Serializer.INSTANCE.readTlv(in, this);
    }
}
