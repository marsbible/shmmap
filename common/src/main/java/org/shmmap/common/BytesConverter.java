package org.shmmap.common;

import net.openhft.chronicle.bytes.Bytes;

import java.io.IOException;

/**
  如果业务类型和map中存储的类型不一致的时候，需要在业务类中实现该接口，在序列化的时候使用
 */
public interface BytesConverter {
    Bytes toBytes() throws IOException;
    void fromBytes(Bytes in) throws IOException;
}