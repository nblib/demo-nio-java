package demo.hewe;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vip.hewe.common.util.FileT;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @year 2017
 * @project demo-nio-java
 * @description:<p></p>
 **/
public class FileTest {
    static final Logger log = LoggerFactory.getLogger(FileTest.class);

    /**
     * 按字节读
     *
     * @throws IOException
     */
    @Test
    public void getStarted_readByte() throws IOException {
        String filePath = FileT.fileAbPath("getstarted.txt", FileTest.class.getClassLoader());
        RandomAccessFile file = new RandomAccessFile(filePath == null ? "" : filePath, "rw");
        FileChannel channel = file.getChannel();
        ByteBuffer buffer = ByteBuffer.allocate(3);
        int read = channel.read(buffer);
        while (read != -1) {
            log.info("read byte: {}", read);

            buffer.flip();
            while (buffer.hasRemaining()) {
                log.info("" + (char) buffer.get());
            }
            buffer.clear();
            read = channel.read(buffer);
        }
        file.close();
    }

    /**
     * 按整个缓冲区读取,如果缓冲区为3的倍数大小,可以读取utf8的中文"你好"
     *
     * @throws IOException
     */
    @Test
    public void getStarted_readUTF8() throws IOException {
        String filePath = FileT.fileAbPath("getstarted.txt", FileTest.class.getClassLoader());
        RandomAccessFile file = new RandomAccessFile(filePath == null ? "" : filePath, "rw");
        FileChannel channel = file.getChannel();
        ByteBuffer buffer = ByteBuffer.allocate(3);
        int read = channel.read(buffer);
        while (read != -1) {
            log.info("read byte: {}", read);
            buffer.flip();
            log.info(new String(buffer.array()));
            buffer.clear();
            read = channel.read(buffer);
        }
        file.close();
    }
}
