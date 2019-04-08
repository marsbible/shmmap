package org.shmmap.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.shmmap.common.BytesSerializer;
import org.shmmap.common.TlvSerializer;
import net.openhft.chronicle.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serializable;

public class Test implements Serializable
{
    private static final long serialVersionUID = -6222408725996885527L;

    public static class Serializer extends TlvSerializer<Test, Test> {
        public static final Serializer INSTANCE = new Serializer();

        @NotNull
        @Override
        public Test read(Bytes in, @Nullable Test using) {
            if(using == null) {
                using = new Test();
            }

            try {
                readTlv(in, using);
                return using;
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            return using;
        }

        @Override
        public void write(Bytes out, @NotNull Test toWrite) {
            try {
                writeTlv(out, toWrite);
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
        protected void writeBody(Bytes out, Test o) {
            out.writeInt(o.downPay);
            out.writeFloat(o.monthly);
        }

        @Override
        protected void readBody(Bytes in, Test o) {
            o.downPay = in.readInt();
            o.monthly = in.readFloat();
        }
    }

    private static ObjectMapper mapper = new ObjectMapper();

    private int downPay;
    private float monthly;

    public Test() {}

    public Test(String json) throws IOException {
        mapper.readerForUpdating(this).readValue(json);
    }

    public Test(Bytes bytes) throws IOException {
        Serializer.INSTANCE.readTlv(bytes, this);
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
}
