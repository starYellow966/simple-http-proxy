import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws IOException {
        /**
         * 不推荐Executors创建线程池：
         * 1、executors返回的对象的弊端：
         *  1） FixedThreadPool 和 SingleThreadPool： 允许的请求队列长度为 Integer.MAX_VALUE，可能会堆积大量的请求，从而导致 OOM。
         *  2） CachedThreadPool： 允许的创建线程数量为 Integer.MAX_VALUE，可能会创建大量的线程，从而导致 OOM
         * 2、通过 ThreadPoolExecutor 的方式，这样的处理方式让写的同学更加明确线程池的运行规则，规避资源耗尽的风险
         */
        ExecutorService executor = new ThreadPoolExecutor(5,5,0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        ServerSocket serverSocket = new ServerSocket(12345);
        while (true) {
            Socket socket = serverSocket.accept();
            executor.execute(new ProxyTask(socket));
        }
    }
}
