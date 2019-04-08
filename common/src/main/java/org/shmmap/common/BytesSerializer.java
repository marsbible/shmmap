package org.shmmap.common;

import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.core.util.ReadResolvable;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;
import org.jetbrains.annotations.NotNull;

public interface BytesSerializer<M> extends BytesReader<M>, BytesWriter<M>, ReadResolvable<BytesSerializer> {
    @Override
    default void readMarshallable(@NotNull WireIn wire) throws IORuntimeException {
        //nothing to read
    }

    @Override
    default void writeMarshallable(@NotNull WireOut wire) {
        //nothing to write
    }

    //序列化后的平均大小
    int avgSize();
}
