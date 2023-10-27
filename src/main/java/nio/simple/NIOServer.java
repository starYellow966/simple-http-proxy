package nio.simple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class NIOServer {

    public void start() throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(12345));
        serverSocketChannel.configureBlocking(false);

        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            if (selector.select() <= 0) {
                continue;
            }

            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    SocketChannel clientChannel = serverSocketChannel.accept();
                    System.out.println("接收到请求:" + clientChannel.getRemoteAddress());
                    clientChannel.configureBlocking(false);
                    clientChannel.register(selector, SelectionKey.OP_READ);
                } else if (key.isReadable()) {
                    SocketChannel clientChannel = (SocketChannel) key.channel();
                    clientChannel.configureBlocking(false);
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                    int bytesRead = 0;
                    while ((bytesRead = clientChannel.read(byteBuffer)) > 0) {
                        byteBuffer.flip();
                        System.out.println(new String(byteBuffer.array(), 0, bytesRead,"UTF-8")); // 打印数据
                        byteBuffer.clear();
                    }
                    if (bytesRead < 0) { // 返回-1，代表通道已经被关闭了
                        if (clientChannel != null) clientChannel.close(); // 关闭了那就相当于断开连接
                        if (key != null) key.cancel();
                    }
                } else if (key.isWritable()) { // TODO 怎么订阅写呢？
                    SocketChannel clientChannel = (SocketChannel) key.channel();
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                    byteBuffer.put("收到了".getBytes(StandardCharsets.UTF_8));
                    byteBuffer.flip();
                    clientChannel.write(byteBuffer);
                    byteBuffer.clear();
                }
            }
        }
    }
}
