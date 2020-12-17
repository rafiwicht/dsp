package ch.rwicht.rbd.ScalarFunctions;

import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.LocalTimeTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.TupleTypeInfo;
import org.apache.flink.table.functions.ScalarFunction;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class CalculateDifference extends ScalarFunction {

    public CalculateDifference() {
    }

    public Long eval(LocalDateTime timestampServer, LocalDateTime timestampClient) {
        return timestampServer.toInstant(ZoneOffset.UTC).toEpochMilli() - timestampClient.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
    public TypeInformation<Long> getResultType(Class<?>[] signature) {
        return BasicTypeInfo.LONG_TYPE_INFO;
    }
}
