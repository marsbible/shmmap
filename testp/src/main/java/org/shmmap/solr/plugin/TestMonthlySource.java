package org.shmmap.solr.plugin;

import org.shmmap.model.TestBytes;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.map.ChronicleMap;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.FloatDocValues;
import org.apache.lucene.search.IndexSearcher;
import org.apache.solr.common.SolrException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class TestMonthlySource extends ValueSource {
    private final ValueSource source;
    private final int plan;
    private Map<Integer,Bytes> imap;
    private final AtomicReference<ChronicleMap<Integer, Bytes>> mapr;

    TestMonthlySource(AtomicReference<ChronicleMap<Integer, Bytes>> mapr, ValueSource source, int plan) {
        if(source == null || mapr.get() == null || plan == 0) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                    "One or more inputs missing for finPrice function");
        }

        this.source = source;
        this.imap = mapr.get();
        this.plan = plan;
        this.mapr = mapr;
    }

    @Override
    public String description() {
        return "";
    }

    @Override
    public boolean equals(Object otherObj) {
        if (otherObj == null || !(otherObj instanceof TestMonthlySource))
            return false;
        TestMonthlySource other = (TestMonthlySource) otherObj;
        return source.equals(other.source) && this.plan == other.plan;
    }

    protected String name() {
        return "TestMonthlySource";
    }

    @Override
    public FunctionValues getValues(Map context, LeafReaderContext readerContext)
            throws IOException {

        final FunctionValues vals =  source.getValues(context, readerContext);


        return new FloatDocValues(this) {

            @Override
            public float floatVal(int doc) {
                float v = vals.floatVal(doc);
                boolean run ;

                do {
                    run = false;
                    try {
                        Bytes x = imap.get(Math.round(v * 10000));

                        if (x != null) {
                            float r = TestBytes.Serializer.INSTANCE.parseMonthly(x, plan);
                            if (r != 0) {
                                return r;
                            }
                        }
                    } catch (IllegalStateException ie) {
                        imap = mapr.get();
                        run = true;
                        System.out.println("TestMonthlySource get failed due to map reload,retry......");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                while(run);

                return v;
            }
        };
    }

    @Override
    public int hashCode() {
        return source.hashCode() + plan;
    }

    @Override
    public void createWeight(Map context, IndexSearcher searcher) throws IOException {
        source.createWeight(context, searcher);
    }
}
