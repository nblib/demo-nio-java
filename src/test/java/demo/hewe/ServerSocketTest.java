package demo.hewe;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * @year 2017
 * @project demo-nio-java
 * @description:<p></p>
 **/
public class ServerSocketTest {
    static final Logger log = LoggerFactory.getLogger(ServerSocketTest.class);

    @Test
    public void testServer() throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(8080));
//        server.configureBlocking(false);
        while (true) {
            System.out.println("start a new accept");
            SocketChannel accept = server.accept();
            if (accept != null) {
                System.out.println("accept" + accept.isConnected());
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                int read = accept.read(buffer);
                while (read != -1) {
                    log.info("read byte: {}", read);
                    buffer.flip();
                    log.info(new String(buffer.array()));
                    buffer.clear();
                    read = accept.read(buffer);
                }
            }
        }
    }
}
