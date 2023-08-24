import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private final static Logger log = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("执行命令格式：java -jar simple-http-proxy-jar-with.dependencies.jar <端口号>");
            System.exit(-1);
        }
        int port = Integer.valueOf(args[0]);

        ExecutorService executor = new ThreadPoolExecutor(5,5,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            while (true) {
                Socket socket = serverSocket.accept();
                log.info("accept connect [" + socket.getRemoteSocketAddress() + "]");
                executor.execute(new SyncProxyRequestHandler(socket));
            }
        } catch (Exception e) {
            System.err.println("创建端口监听失败，请检查端口是否被占用, e=" + e);
        }
    }
}
