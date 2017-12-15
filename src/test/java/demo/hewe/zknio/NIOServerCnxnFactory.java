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
public class NIOServerCnxnFactory {
    private static final Logger LOG = LoggerFactory.getLogger(NIOServerCnxnFactory.class);
    private NIOServerCnxnFactory.AcceptThread acceptThread;
    //selectorThreads
    private final Set<SelectorThread> selectorThreads =
            new HashSet<SelectorThread>();
    /**
     * selectorThread 数量
     */
    private static final int NUMSELECTORTHREADS = 1;        // 32 cores sweet spot seems to be 4 selector threads

    /**
     * zookeeper是否停止
     */
    private volatile boolean stopped = true;

    protected ZooKeeperServer zkServer;

    public static void main(String[] args) throws IOException, InterruptedException {
        NIOServerCnxnFactory cnxn = new NIOServerCnxnFactory();
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

        //创建selectorThread
        for (int i = 0; i < NUMSELECTORTHREADS; i++) {
            selectorThreads.add(new SelectorThread(i));
        }
        //创建监听线程
        ServerSocketChannel ss = ServerSocketChannel.open();
        ss.socket().setReuseAddress(true);
        LOG.info("binding to port: ---->>{}", addr);
        ss.socket().bind(addr);
        ss.configureBlocking(false);
        acceptThread = new AcceptThread(ss, selectorThreads);
    }

    /**
     * 开启Accept线程
     */
    public void start() {
        //更新标识位
        stopped = false;
        if (acceptThread.getState() == Thread.State.NEW) {
            acceptThread.start();
        }
        //启动selectorThreads
        for (SelectorThread thread : selectorThreads) {
            if (thread.getState() == Thread.State.NEW) {
                thread.start();
            }
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
        //加入线程等待
        for (SelectorThread thread : selectorThreads) {
            thread.join();
        }
    }

    /**
     * accept线程,开启serverSocket监听
     */
    private class AcceptThread extends Thread {
        private final ServerSocketChannel acceptSocket;
        private final Selector selector;

        private final Collection<SelectorThread> selectorThreads;
        private Iterator<SelectorThread> selectorIterator;


        public AcceptThread(ServerSocketChannel ss, Set<SelectorThread> selectorThreads) throws IOException {
            this.acceptSocket = ss;
            selector = Selector.open();
            acceptSocket.register(selector, SelectionKey.OP_ACCEPT);
            this.selectorThreads = Collections.unmodifiableList(
                    new ArrayList<SelectorThread>(selectorThreads));
            selectorIterator = this.selectorThreads.iterator();
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
                LOG.debug("selectedKeys size: ---->>{}", selector.selectedKeys().size());
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
                LOG.info("Accepted socket connection from ---->>"
                        + sc.socket().getRemoteSocketAddress());
                //接收到连接后,开始处理
                sc.configureBlocking(false);
                //从selectorThreads中选择一个线程
                if (!selectorIterator.hasNext()) {
                    selectorIterator = selectorThreads.iterator();
                }
                SelectorThread selectorThread = selectorIterator.next();
                //收到的连接添加到selectorThread中
                if (!selectorThread.addAcceptedConnection(sc)) {
                    throw new IOException(
                            "Unable to add connection to selector queue"
                                    + (stopped ? " (shutdown in progress)" : ""));
                }
            } catch (IOException e) {
                LOG.warn("Error accepting new connection: ", e);
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
                    // 注册acceptqueue中的连接到selector中.
                    processAcceptedConnections();
                }
            } catch (Exception e) {
                LOG.warn("Ignoring unexpected exception", e);
            }
        }

        /**
         *
         */
        private void select() {
            try {
                selector.select();
                LOG.trace("receive a select event or wakeup sign");
                Set<SelectionKey> selected = selector.selectedKeys();
                ArrayList<SelectionKey> selectedList =
                        new ArrayList<SelectionKey>(selected);
                //打乱list中的内容排序
                Collections.shuffle(selectedList);
                LOG.trace("selected keys total: ---->>{}", selectedList.size());
                Iterator<SelectionKey> selectedKeys = selectedList.iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    selected.remove(key);

                    if (!key.isValid()) {
                        LOG.trace("key is invalid");
                        cleanupSelectionKey(key);
                        continue;
                    }
                    if (key.isReadable() || key.isWritable()) {
                        handleIO(key);
                        LOG.debug("key is Readable or writable");

                    } else {
                        LOG.warn("Unexpected ops in select " + key.readyOps());
                    }
                }
            } catch (Exception e) {
                LOG.warn("Ignoring IOException while selecting", e);
            }
        }

        /**
         * 新建一个IOWorkRequest,交给workPool进行调度处理key收到的事件
         *
         * @param key
         */
        private void handleIO(SelectionKey key) {
            //TODO 新建一个 IOWorkRequest

        }

        /**
         * 处理发送到当前线程但还没有在selector中注册的连接
         */
        private void processAcceptedConnections() {
            SocketChannel accepted;
            while (!stopped && (accepted = acceptedQueue.poll()) != null) {
                LOG.trace("get a connection from acceptedQueue,regist to selector");
                SelectionKey key = null;
                try {
                    key = accepted.register(selector, SelectionKey.OP_READ);
                    //TODO 注册到selector中了,但是还没有设置关联一个ServerCnxn来处理消息
                    NIOServerCnxn cnxn = createConnection(accepted, key, this);
                    key.attach(cnxn);
                } catch (IOException e) {
                    // register, createConnection
                    cleanupSelectionKey(key);
                }
            }
        }

        /**
         * 清除key,也就是注册在这个selector中的channel注册的监听信息不会被selector再处理,除非重新注册
         * 测试: 建立一个连接,不清除key,也不处理key中的内容.那么会一直循环select(),清除后,再次发送消息select()也不会工作.
         *
         * @param key
         */
        private void cleanupSelectionKey(SelectionKey key) {
            if (key != null) {
                key.cancel();
            }
        }

        /**
         * 把一个到来的连接添加到队列中,同时唤醒select()
         *
         * @param accepted
         * @return
         */
        public boolean addAcceptedConnection(SocketChannel accepted) {
            if (stopped || !acceptedQueue.offer(accepted)) {
                return false;
            }
            LOG.trace("add accepted connection to queue,size of queue currently is : ---->>{}", acceptedQueue.size());
            wakeupSelector();
            return true;
        }

        /**
         * 唤醒selector停止等待select(),开始下一步
         */
        public void wakeupSelector() {
            LOG.trace("wakeup a selector");
            selector.wakeup();
        }

    }
    //TODO 创建IOWorkRequest和 WorkerService
//    private class IOWorkRequest extends WorkerService.WorkRequest {
//        private final SelectorThread selectorThread;
//        private final SelectionKey key;
//        private final NIOServerCnxn cnxn;
//
//        IOWorkRequest(SelectorThread selectorThread, SelectionKey key) {
//            this.selectorThread = selectorThread;
//            this.key = key;
//            this.cnxn = (NIOServerCnxn) key.attachment();
//        }
//    }

    protected NIOServerCnxn createConnection(SocketChannel sock,
                                             SelectionKey sk, SelectorThread selectorThread) throws IOException {
        return new NIOServerCnxn(zkServer, sock, sk, this, selectorThread);
    }
}
