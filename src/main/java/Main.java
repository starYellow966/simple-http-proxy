import java.io.IOException;

import nio.simple.NIOClient;
import nio.simple.NIOServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private final static Logger log = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) throws InterruptedException, IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    new NIOServer().start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        Thread.sleep(10);

        new NIOClient().echo();
    }
}
