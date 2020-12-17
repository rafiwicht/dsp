package ch.rwicht.rbd.scalar;

import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.table.functions.ScalarFunction;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class CalculateDifference extends ScalarFunction {

    public CalculateDifference() {
    }

    public Long eval(Long timestampServer, Long timestampClient) {
        return timestampClient - timestampServer;
    }
    public TypeInformation<Long> getResultType(Class<?>[] signature) {
        return BasicTypeInfo.LONG_TYPE_INFO;
    }
}
