import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 基于同步IO使用的代理请求处理器，其中每一个客户端的socket，创建一个handler
 * <p>
 * 处理流程：<br>
 * 1、解析第一个请求<br>
 * 2、根据不同的请求类型，建立不同的连接<br>
 *  a) 对于CONNECT HTTP请求，先与目标服务器建立连接后，先回一个「Connection Established」给客户端，再将原请求中的body数据发给目标服务器<br>
 *  b) 对于其他HTTP请求，则与目标服务器建立连接后，直接将数据原封不动发送<br>
 * 3、一直等待「与客户端的连接」和「与目标服务器的连接」<br>
 *  a) 客户端有数据来，直接转发给目标服务器<br>
 *  b) 目标服务器有数据来，直接转发给客户端<br>
 * </p>
 * @author goldhuang
 */
public class SyncProxyRequestHandler implements Runnable{
    /**
     * 与客户端建立的socket
     */
    Socket socketClient;

    /**
     * 与目标服务器建立的socket
     */
    Socket socketServer;

    public SyncProxyRequestHandler(Socket socket){
        this.socketClient = socket;
    }

    private static final Logger log = LoggerFactory.getLogger(SyncProxyRequestHandler.class);
    private static final List<String> HTTP_METHODS = Arrays.asList("GET", "POST", "PUT", "HEAD", "DELETE");
    private static final List<String> HTTPS_METHODS = Arrays.asList("CONNECT");

    @Override
    public void run() {
        try {
            // step0 与客户端连接的socket的输入输出流
            InputStream clientInput = new BufferedInputStream(socketClient.getInputStream());
            OutputStream clientOutput = new BufferedOutputStream(socketClient.getOutputStream());

            // step1 读取第一个请求并解析
            byte[] clientMessage = read(clientInput, true);
            HttpRequest httpRequest = splitRequest(new String(clientMessage, StandardCharsets.UTF_8));
            if (httpRequest.getServerHost() == null || httpRequest.getServerHost().equals("")) {
                clientOutput.write("request host is empty".getBytes(StandardCharsets.UTF_8));
                clientOutput.flush();
                return;
            }

            // step2 建立与目标服务器的连接
            socketServer = new Socket(httpRequest.getServerHost(), httpRequest.getServerPort());
            socketServer.setSoTimeout(5000);
            log.info("success connect server [" + socketServer.getRemoteSocketAddress() + "]");
            InputStream serverInput = new BufferedInputStream(socketServer.getInputStream());
            OutputStream serverOutput = new BufferedOutputStream(socketServer.getOutputStream());

            // step3 根据不同的请求方式，执行不同的操作
            if (HTTP_METHODS.contains(httpRequest.getMethod())) {
                write(serverOutput, clientMessage, false);
            } else if (HTTPS_METHODS.contains(httpRequest.getMethod())) {
                // connect请求需要再与目标服务器建立成功后，马上回一个包
                write(clientOutput, String.format("%s %d Connection Established\r\nConnection: close\r\n\r\n", httpRequest.getVersion(), 200).getBytes(StandardCharsets.UTF_8), false); // 完成连接，通知客户端
                write(serverOutput, read(clientInput, true), false); // 再将真正的数据告诉给目标服务器
            }


            // step4 监听两个socket，进行数据转发
            int idleCount = 0; // 空转次数
            while (true) {
                boolean isIdle = true;
                // 先判断有没有数据，再进行读。避免阻塞
                if (serverInput.available() > 0) {
                    write(clientOutput, read(serverInput, false), true);
                    isIdle = false;
                }
                if (clientInput.available() > 0) {
                    write(serverOutput, read(clientInput, true), false);
                    isIdle = false;
                }
                if (isIdle) {
                    idleCount++;
                    if (idleCount > 5 && !isConnected()) { // 空转超过5次，就通过心跳判断socket的连通性
                        break;
                    } else if (idleCount > 5) {
                        idleCount = 0;
                    }
                }
                Thread.sleep(1000); // 休息一下
            }
        } catch (Exception e) {
            log.error("handle socket=[{}] failed, e=", socketClient.getRemoteSocketAddress(), e);
        } finally {
            try {
                if (this.socketClient != null) {
                    log.info("close socketClient=[{}]", socketClient.getRemoteSocketAddress());
                    this.socketClient.close();
                }
                if (this.socketServer != null) {
                    log.info("close socketServer=[{}]", socketServer.getRemoteSocketAddress());
                    this.socketServer.close();
                }
            } catch (Exception e) {
                log.error("close socket failed, e=", e);
            }
        }
    }

    /**
     * 将整个请求内容进行解析成HttpRequest。仅在第一次建立各方连接时使用到
     * @param request 请求完整内容
     * @return 结构化的请求内容
     */
    public HttpRequest splitRequest(String request) {
        try {
            HttpRequest httpRequest = new HttpRequest();

            // [0] -- header
            // [1] -- body
            List<String> requestArrays = Arrays.asList(request.split("\r\n\r\n"));
            List<String> headers = Arrays.asList(requestArrays.get(0).split("\r\n"));
            if (requestArrays.size() > 1) {
                httpRequest.setBody(requestArrays.get(1));
                httpRequest.setOriginBody(requestArrays.get(1).getBytes(StandardCharsets.UTF_8));
            }

            // 请求行
            List<String> requestLines = Arrays.asList(headers.get(0).split(" "));
            httpRequest.setMethod(requestLines.get(0));
            httpRequest.setUrl(requestLines.get(1));
            httpRequest.setVersion(requestLines.get(2));

            Map<String,String> requestHeader = new HashMap<>();
            for (int i=1; i<headers.size(); i++) {
                String[] kv = headers.get(i).split(": "); // 有一个空格
                requestHeader.put(kv[0].trim(), kv[1].trim());
            }
            if (requestHeader.containsKey("Host")) {
                String[] host = requestHeader.get("Host").split(":");
                httpRequest.setServerHost(host[0]);
                if (host.length > 1) {
                    httpRequest.setServerPort(Integer.valueOf(host[1]));
                }
            }

            if (httpRequest.getServerPort() == 0) {
                if (HTTP_METHODS.contains(httpRequest.getMethod())) {httpRequest.setServerPort(80);}
                else if (HTTPS_METHODS.contains(httpRequest.getMethod())) {httpRequest.setServerPort(443);}
            }

            return httpRequest;
        } catch (Exception e) {
            log.error("splitRequest failed, e=", e);
            throw e;
        }
    }

    public byte[] read(InputStream inputStream, boolean printFlag) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int size;
        do {
            size = inputStream.read(buffer);
            if (size < 0) {
                break;
            }
            byteArrayOutputStream.write(Arrays.copyOf(buffer, size));
        } while (inputStream.available() > 0); // 说明还有数据可以读。非阻塞方法
        if (printFlag) {
            print(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8), true, true);
        } else {
            print(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8), true, false);
        }
        return byteArrayOutputStream.toByteArray();
    }

    public void write(OutputStream writer, byte[] msg, boolean printFlag) throws IOException {
        if (writer == null || msg == null || msg.length == 0) {
            return;
        }

        if (printFlag) {
            print(new String(msg, StandardCharsets.UTF_8), false,true);
        } else {
            print(new String(msg, StandardCharsets.UTF_8), false, false);
        }

        writer.write(msg);
        writer.flush();
    }

    /**
     * 格式化打印
     */
    private void print(String msg, boolean isInput, boolean isClient) {
        if (isClient) {
            if (isInput) {
                log.info("[client[{}]->proxy] {}", this.socketClient.getRemoteSocketAddress(), msg);
            } else {
                log.info("[proxy->client[{}]] {}", this.socketClient.getRemoteSocketAddress(), msg);
            }
        } else {
            if (isInput) {
                log.info("[server[{}]->proxy] {}", this.socketServer.getRemoteSocketAddress(), msg);
            } else {
                log.info("[proxy->server[{}]] {}", this.socketServer.getRemoteSocketAddress(), msg);
            }
        }
    }

    /**
     * 判断socket已经关闭
     * <p>
     *     原生的socket.isClose()、isConnect() 只能判断己端是否关闭连接，如果对端关闭了连接，这两个函数是感知不到的。
     *     所以这里写了一个函数，通过主动发送数据（类似于心跳）来判断socket是否已经关闭
     * </p>
     * @return
     */
    private boolean isConnected() {
        try {
            this.socketClient.getOutputStream().write(0xFF);
            read(this.socketClient.getInputStream(), true);
            this.socketServer.getOutputStream().write(0xFF);
            read(this.socketServer.getInputStream(), true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
