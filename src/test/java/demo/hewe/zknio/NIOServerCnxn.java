package demo.hewe.zknio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * 负责处理和客户端的数据沟通
 **/
public class NIOServerCnxn {
    private static final Logger LOG = LoggerFactory.getLogger(NIOServerCnxn.class);

    private final NIOServerCnxnFactory factory;

    private final SocketChannel sock;

    private final NIOServerCnxnFactory.SelectorThread selectorThread;

    private final SelectionKey sk;
    private final ZooKeeperServer zkServer;

    //首先读取连接发送过来的前四个字节,保存的是,消息的总长度
    private final ByteBuffer lenBuffer = ByteBuffer.allocate(4);
    private ByteBuffer incomingBuffer = lenBuffer;

    //是否完成初始化
    private boolean initialized = false;

    public NIOServerCnxn(ZooKeeperServer zk, SocketChannel sock,
                         SelectionKey sk, NIOServerCnxnFactory factory,
                         NIOServerCnxnFactory.SelectorThread selectorThread) throws IOException {
        this.zkServer = zk;
        this.sock = sock;
        this.sk = sk;
        this.factory = factory;
        this.selectorThread = selectorThread;
        sock.socket().setTcpNoDelay(true);
        /* set socket linger to false, so that socket close does not block */
        sock.socket().setSoLinger(false, -1);
    }

    void doIO(SelectionKey key) {
        try {
            if (key.isReadable()) {
                int rc = sock.read(incomingBuffer);
                if (rc < 0) {
                    throw new IllegalStateException(
                            "Unable to read additional data from client"
                                    + ", likely client has closed socket");
                }
                if (incomingBuffer.remaining() == 0) {
                    boolean isPayload;
                    if (incomingBuffer == lenBuffer) { // start of next request
                        incomingBuffer.flip();
                        //读取长度
                        isPayload = readLength();
                        incomingBuffer.clear();
                    } else {
                        // continuation
                        isPayload = true;
                    }
                    if (isPayload) { // 消息的长度读到了,接下来该读取真正的数据了
                        readPayload();
                    } else {
                        // four letter words take care
                        // need not do anything else
                        return;
                    }
                }
            }
            if (key.isWritable()) {
                //TODO key is writable, waiting for handle
                LOG.warn("key is writable,waiting for handle");
            }
        } catch (IOException ioe) {
            LOG.warn("Exception causing: ", ioe);
        }
    }

    private void readPayload() throws IOException {
        if (incomingBuffer.remaining() != 0) { //还没有读完,接着读
            int rc = sock.read(incomingBuffer);
            if (rc < 0) {
                throw new IllegalStateException(
                        "Unable to read additional data from client"
                                + ", likely client has closed socket");
            }
        }
        if (incomingBuffer.remaining() == 0) { //读完了,收到了完整数据
            //开始从缓冲区往出读
            //TODO 开始从缓冲区往出读
            incomingBuffer.flip();
            if (!initialized) {
                //readConnectRequest();
            } else {
                //readRequest();
            }
            lenBuffer.clear();
            incomingBuffer = lenBuffer;
        }
    }

    /**
     * 从lenBuffer中读取这次发送消息的总长度,方便incomingBuffer申请BUffer用来存放.
     *
     * @return 是否正确的读到了长度
     * @throws IOException
     */
    private boolean readLength() throws IOException {
        // Read the length, now get the buffer
        int len = lenBuffer.getInt();
        LOG.trace("len get from client message header is : ---->>{}", len);
        incomingBuffer = ByteBuffer.allocate(len);
        return true;
    }
}
