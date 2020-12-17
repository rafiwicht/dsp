import java.nio.ByteBuffer;
import java.util.Arrays;

public class JavaTest 
{
 
       public static void main (String[] args)
       {
             String gugus = "gugus";
             byte[] byteArray = gugus.getBytes();
             System.out.println(JavaTest.byteArrayToInt(byteArray));
       }

       public static int byteArrayToInt(byte[] b) 
        {
            return   b[3] & 0xFF |
                    (b[2] & 0xFF) << 8 |
                    (b[1] & 0xFF) << 16 |
                    (b[0] & 0xFF) << 24;
        }
}