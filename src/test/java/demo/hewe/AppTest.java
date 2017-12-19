package demo.hewe;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Unit test for simple App.
 */
public class AppTest {
    @Test
    public void testSocket() throws IOException {
        Socket s = new Socket("10.169.3.219", 3579);

        OutputStream outputStream = s.getOutputStream();
        outputStream.write("nihao\n".getBytes());
        outputStream.close();
        s.close();
    }
}


/*


curl -X POST  https://rest.nexmo.com/sms/json \
-d api_key=e6675d19 \
-d api_secret=5b2ac835fda0482d \
-d to=18233293969 \
-d from="NEXMO" \
-d text="Hello from Nexmo"
 */