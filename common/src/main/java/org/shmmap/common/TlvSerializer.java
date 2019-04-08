package org.shmmap.common;

import net.openhft.chronicle.bytes.Bytes;

import java.io.IOException;

/**
 * 简单的TLV编解码基类，对空间占用要求不高的可以直接使用
 * T表示业务实体类型，M表示map中存储的类型，二者可以一样也可以不同，基于使用的ChronicleMap，
 *  M = T || Bytes
 */
public abstract class TlvSerializer<T,M> implements BytesSerializer<M>
{
    private int typeCode = this.getClass().getSimpleName().hashCode() & 0x7fff;

    protected void writeHead(Bytes out) {
        out.writeShort((short)(typeCode));
        out.writeShort((short)0);
    }

    protected int readHead(Bytes in) throws IOException {
        short type = in.readShort();
        short len = in.readShort();

        if(type != typeCode) {
            throw new IOException("read type " + type + "doesn't match expected " + typeCode);
        }
        return len;
    }

    public int getLen(Bytes in) throws IOException{
        return readHead(in);
    }

    protected abstract void writeBody(Bytes out, T o);
    protected abstract void readBody(Bytes in, T o);

    public void writeTlv(Bytes out,T toWrite) throws IOException{
        writeHead(out);
        long head = out.writePosition();
        writeBody(out, toWrite);

        long x = out.writePosition() - head;
        if(x > 65535) {
            throw new IOException("written bytes must not exceed 64k.");
        }

        out.writeShort(head - 2, (short)x);
    }

    public void readTlv(Bytes in, T using) throws IOException{
        int len = readHead(in);

        readBody(in, using);
    }
}