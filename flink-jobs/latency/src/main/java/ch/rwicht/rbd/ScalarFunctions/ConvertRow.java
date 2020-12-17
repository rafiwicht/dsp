package ch.rwicht.rbd.ScalarFunctions;

import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple5;
import org.apache.flink.api.java.typeutils.TupleTypeInfo;
import org.apache.flink.table.functions.ScalarFunction;

public class ConvertRow extends ScalarFunction {

    public ConvertRow() {}

    public Tuple5<Long, Long, Long, Integer, Long> eval(long min, long max, long avg, long timestamp) {
        int date = (int) timestamp / 86400;
        return new Tuple5<>(min, max, avg, date, timestamp);
    }

    public TypeInformation<Tuple5<Long, Long, Long, Integer, Long>> getResultType(Class<?>[] signature) {
        return new TupleTypeInfo<>(BasicTypeInfo.LONG_TYPE_INFO, BasicTypeInfo.LONG_TYPE_INFO, BasicTypeInfo.LONG_TYPE_INFO, BasicTypeInfo.INT_TYPE_INFO, BasicTypeInfo.LONG_TYPE_INFO);
    }
}
