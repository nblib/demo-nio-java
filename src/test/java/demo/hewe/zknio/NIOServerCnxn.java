package demo.hewe.zknio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @year 2017
 * @project demo-nio-java
 * @description:<p></p>
 **/
public class NIOServerCnxn {
    private static final Logger LOG = LoggerFactory.getLogger(NIOServerCnxn.class);
    private NIOServerCnxn.AcceptThread acceptThread;

    public static void main(String[] args) throws IOException, InterruptedException {
        NIOServerCnxn cnxn = new NIOServerCnxn();
        cnxn.configure(new InetSocketAddress(8080));
        cnxn.start();
        cnxn.join();
    }

    /**
     * 配置并新建一个AccpetThread
     *
     * @param addr
     * @throws IOException
     */
    public void configure(InetSocketAddress addr) throws IOException {
        ServerSocketChannel ss = ServerSocketChannel.open();
        ss.socket().setReuseAddress(true);
        LOG.info("binding to port " + addr);
        ss.socket().bind(addr);
        ss.configureBlocking(false);
        acceptThread = new AcceptThread(ss);
    }

    /**
     * 开启Accept线程
     */
    public void start() {
        if (acceptThread.getState() == Thread.State.NEW) {
            acceptThread.start();
        }
    }

    /**
     * join
     *
     * @throws InterruptedException
     */
    public void join() throws InterruptedException {
        if (acceptThread != null) {
            acceptThread.join();
        }
    }

    /**
     * accept线程,开启serverSocket监听
     */
    private class AcceptThread extends Thread {
        private final ServerSocketChannel acceptSocket;
        private final Selector selector;

        public AcceptThread(ServerSocketChannel ss) throws IOException {
            this.acceptSocket = ss;
            selector = Selector.open();
            acceptSocket.register(selector, SelectionKey.OP_ACCEPT);
        }

        @Override
        public void run() {
            try {
                while (!acceptSocket.socket().isClosed()) {
                    select();
                }
            } finally {
                try {
                    selector.close();
                } catch (IOException e) {
                    LOG.error("close selector error:", e);
                }
            }
        }

        /**
         * select到来的连接
         */
        private void select() {
            try {
                selector.select();
                Iterator<SelectionKey> selectedKeys =
                        selector.selectedKeys().iterator();
                LOG.debug("selectedKeys size: {}", selector.selectedKeys().size());
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isAcceptable()) {
                        doAccept();
                    } else {
                        LOG.warn("Unexpected ops in accept select "
                                + key.readyOps());
                    }
                }
            } catch (Exception e) {
                LOG.warn("Ignoring IOException while selecting", e);
            }

        }

        private void doAccept() {
            SocketChannel sc = null;
            try {
                sc = acceptSocket.accept();
                LOG.info("Accepted socket connection from "
                        + sc.socket().getRemoteSocketAddress());
            } catch (IOException e) {
                LOG.warn("Error accepting new connection: " + e.getMessage());
            }
        }
    }

    /**
     * accpetthread接收到一个连接的时候,将连接交由selectorthread处理.
     */
    class SelectorThread extends Thread {
        private final int id;
        private final Queue<SocketChannel> acceptedQueue;
        private final Selector selector;

        public SelectorThread(int id) throws IOException {
            this.id = id;
            selector = Selector.open();
            acceptedQueue = new LinkedBlockingQueue<SocketChannel>();
        }

        @Override
        public void run() {
            try {
                while (true) {
                    select();
                }
            } catch (Exception e) {
                LOG.warn("Ignoring unexpected exception", e);
            }
        }

        private void select() {
            try {
                selector.select();

                Set<SelectionKey> selected = selector.selectedKeys();
                ArrayList<SelectionKey> selectedList =
                        new ArrayList<SelectionKey>(selected);
                Collections.shuffle(selectedList);
                Iterator<SelectionKey> selectedKeys = selectedList.iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    selected.remove(key);

                    if (!key.isValid()) {
                        LOG.debug("key is invalid");
                        cleanupSelectionKey(key);
                        continue;
                    }
                    if (key.isReadable() || key.isWritable()) {
                        //handleIO(key);
                        LOG.debug("key is Readable or writable");
                    } else {
                        LOG.warn("Unexpected ops in select " + key.readyOps());
                    }
                }
            } catch (Exception e) {
                LOG.warn("Ignoring IOException while selecting", e);
            }
        }

        private void cleanupSelectionKey(SelectionKey key) {
            if (key != null) {
                key.cancel();
            }
        }
    }
}
