import com.alibaba.fastjson.JSON;
import lombok.SneakyThrows;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 每一个客户端的socket，创建一个task
 */
public class ProxyTask implements Runnable{
    /**
     * 与客户端建立的socket
     */
    Socket socketClient;

    /**
     * 与目标服务器建立的socket
     */
    Socket socketServer;

    public ProxyTask(Socket socket){
        this.socketClient = socket;
    }

    private static final List<String> HTTP_METHOD = Arrays.asList("GET", "POST", "PUT", "HEAD", "DELETE");
    private static final List<String> HTTPS_METHODS = Arrays.asList("CONNECT");

    @SneakyThrows
    @Override
    public void run() {


        // TODO reader 和 inputStream 的区别？
        try {
            // step0 与客户端连接的socket的输入输出流
            BufferedReader clientReader = new BufferedReader(new InputStreamReader(socketClient.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter clientWriter = new BufferedWriter(new OutputStreamWriter(socketClient.getOutputStream(), StandardCharsets.UTF_8));

            // step1 读取请求
            String clientMessage = read(clientReader, true);

            // step2 分离头部和内容
            HttpRequest httpRequest = splitRequest(clientMessage);
            if (httpRequest.getServerHost() == null || httpRequest.getServerHost().equals("")) {
                clientWriter.write("request host is empty");
                clientWriter.flush();
                return;
            }

            // step3 根据头部判断是https还是http
            BufferedReader serverReader = null;
            BufferedWriter serverWriter = null;
            if (HTTP_METHOD.contains(httpRequest.getMethod())) {
                socketServer = new Socket(httpRequest.getServerHost(), httpRequest.getServerPort() == 0 ? 80 : httpRequest.getServerPort());
                serverReader = new BufferedReader(new InputStreamReader(socketServer.getInputStream(), StandardCharsets.UTF_8));
                serverWriter = new BufferedWriter(new OutputStreamWriter(socketServer.getOutputStream(), StandardCharsets.UTF_8));
                write(serverWriter, httpRequest.getBody(), false);
            } else if (HTTPS_METHODS.contains(httpRequest.getMethod())) {
                socketServer = new Socket(httpRequest.getServerHost(), httpRequest.getServerPort() == 0 ? 443 : httpRequest.getServerPort());
                serverReader = new BufferedReader(new InputStreamReader(socketServer.getInputStream(), StandardCharsets.UTF_8));
                serverWriter = new BufferedWriter(new OutputStreamWriter(socketServer.getOutputStream(), StandardCharsets.UTF_8));
                // connect请求需要再与目标服务器建立成功后，马上回一个包
                write(serverWriter, String.format("%s %d Connection Established\\r\\nConnection: close\\r\\n\\r\\n", httpRequest.getVersion(), 200), false); // 完成连接，通知客户端
                write(serverWriter, read(clientReader, true), false); // 再将真正的数据告诉给目标服务器
            }


            // step5 监听两个socket
            while (true) {
                if (clientReader.ready()) {
                    write(serverWriter, read(clientReader, true), false);
                }
                if (serverReader.ready()) {
                    write(clientWriter, read(serverReader, false), true);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.socketClient.close();
            this.socketServer.close();
        }
    }

    /**
     * 将整个请求内容进行解析成HttpRequest
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
//            System.out.println(JSON.toJSONString(httpRequest));
            return httpRequest;
        } catch (Exception e) {
            // TODO 解析错误，回一包给客户端
            e.printStackTrace();
            throw e;
        }
    }

    public String read(BufferedReader reader, boolean printFlag) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        while (reader.ready()) {
            char[] buff = new char[2048];
            int size = reader.read(buff);
            stringBuilder.append(Arrays.copyOf(buff, size));
        }

        if (printFlag) {
            System.out.println("[client->proxy] " + stringBuilder.toString());
        } else {
            System.out.println("[server->proxy] " + stringBuilder.toString());
        }
        return stringBuilder.toString();
    }

    public void write(BufferedWriter writer, String msg, boolean printFlag) throws IOException {
        if (writer == null || msg == null) {
            return;
        }

        if (printFlag) {
            System.out.println("[proxy->client] " + msg);
        } else {
            System.out.println("[proxy->server]" + msg);
        }

        writer.write(msg);
        writer.flush();
    }
}
