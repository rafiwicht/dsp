package ch.rwicht.rbd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.pmml4s.model.Model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws IOException
    {
        // create flink event
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        node.put("timestamp", 1586625607000.0);
        node.put("method", "GET");
        node.put("httpCode", 403);
        node.put("userAgent", "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0; BOIE9;ENUS),Opera/9.20 (Windows NT 6.0; U; en)");

        // import model
        InputStream is = App.class.getClassLoader().getResourceAsStream("model.pmml");
        Model model = Model.fromInputStream(is);

        Map<String, Object> result = model.predict(new HashMap<String, Object>() {{
            put("timestamp", node.get("timestamp").asLong());
            put("method", byteArrayToInt(node.get("method").asText().getBytes()));
            put("httpCode", node.get("httpCode").asInt());
            put("userAgent",  byteArrayToInt(node.get("userAgent").asText().getBytes()));
        }});

        System.out.println((double)result.get("probability(1)") > 0.5);

    }

    public static int byteArrayToInt(byte[] b)
    {
        if(b.length == 0) return 0;
        return   b[2] & 0xFF |
                (b[1] & 0xFF) << 8 |
                (b[0] & 0xFF) << 16;
    }
}
