import lombok.Data;
import java.util.Map;

@Data
public class HttpRequest {
    private String url;
    private String method;
    private String version;
    private Map<String,String> header;
    private String body;
    private String serverHost; // 目标服务器的host
    private int serverPort; // 目标服务器的port

//    public HttpRequest() {};
}
