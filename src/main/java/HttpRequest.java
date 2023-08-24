import java.util.Map;

public class HttpRequest {
    private String url;
    private String method;
    private String version;
    private Map<String,String> header;
    private String body;
    private byte[] originBody;
    private String serverHost; // 目标服务器的host
    private int serverPort; // 目标服务器的port

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<String, String> getHeader() {
        return header;
    }

    public void setHeader(Map<String, String> header) {
        this.header = header;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public byte[] getOriginBody() {
        return originBody;
    }

    public void setOriginBody(byte[] originBody) {
        this.originBody = originBody;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }
}
