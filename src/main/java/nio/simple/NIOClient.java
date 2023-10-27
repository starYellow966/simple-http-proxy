package nio.simple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class NIOClient {
    public void echo() throws IOException, InterruptedException {
        SocketChannel channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(12345));
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        byteBuffer.put("hello world".getBytes(StandardCharsets.UTF_8));
        byteBuffer.flip();
        channel.write(byteBuffer);
        byteBuffer.clear();

        channel.read(byteBuffer);
        byteBuffer.flip();
        System.out.println(new String(byteBuffer.array()));
        byteBuffer.clear();
        System.out.println("client finish");
    }
}
