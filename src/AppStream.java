import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.littlechou.json.JsonValidator;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppStream extends WebSocketServer {

    /**
     * 一群使用者 塞到 HashMap client_id => Websocket Object
     */
    private HashMap<String,WebSocket> clients = new HashMap<String, WebSocket>();


    public AppStream( int port ) throws UnknownHostException {
        super( new InetSocketAddress( port ) );
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        try {
            // 抓回 Token 資料
            String token = null;
            if (clientHandshake.getFieldValue("token") != null) {
                token = clientHandshake.getFieldValue("token");
            }
            // client 端有可能用 GET 的方式傳值 (?)
            Map<String, String> params = getParams(webSocket.getResourceDescriptor());
            if (params.get("token") != null) {
                token = params.get("token");
            }
            // 解析 JWT Token
            // @see https://github.com/auth0/java-jwt#decode-a-token
            DecodedJWT jwt = JWT.decode(token);
            // @see https://github.com/auth0/java-jwt#claim-class
            Map<String, Claim> claimMap = jwt.getClaims();
            Claim claim = claimMap.get("serial");
            String userSerial = claim.asString();
            System.out.println("serial = " + userSerial);
            // 向遠端伺服器抓回資料
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("http://chat.lara.local/api/member/" + userSerial)
                    .build();
            Response response = client.newCall(request).execute();
            String responseString;
            responseString = response.body().string();
            JSONObject jsonObject = new JSONObject(responseString);
            // 廣播給所有的在線上的使用者
            broadcast("welcome " + jsonObject.getString("name") + "\t connection server");
            // 存入 HashMap
            clients.put(userSerial,webSocket);
        } catch (JWTDecodeException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String,String> getParams(String urlParams) throws Exception {
        String url = "http://localhost" + urlParams;

        List<NameValuePair> param = URLEncodedUtils.parse(new URI(url), String.valueOf(Charset.forName("UTF-8")));

        HashMap<String, String> paramMap = new HashMap<>();
        for (NameValuePair item : param) {
            paramMap.put(item.getName(),item.getValue());
        }

        return paramMap;
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {

    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        JsonValidator jsonValidator = new JsonValidator();
        // 合法的就解析
        if (jsonValidator.validate(s)) {
            JSONObject jsonReqObject = new JSONObject(s);

            // 抓 發送使用者的對象
            String sendTargetUserSerial = jsonReqObject.getString("user_serial");

            if (sendTargetUserSerial != null) {
                WebSocket targetUser = clients.get(sendTargetUserSerial);
                Map result = new HashMap();
                result.put("action","send_message");
                result.put("message", jsonReqObject.getString("message"));
                JSONObject jsonResponseObj = new JSONObject(result);
                targetUser.send(jsonResponseObj.toString());
            }
        }
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {

    }

    @Override
    public void onStart() {
        System.out.println("Server started!");
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }

    public static void main(String[] args) throws InterruptedException , IOException {
        AppStream appStream = new AppStream(4000);
        appStream.start();

        System.out.println( "ChatServer started on port: " + appStream.getPort() );

        BufferedReader sysin = new BufferedReader( new InputStreamReader( System.in ) );
        while ( true ) {
            String in = sysin.readLine();
            appStream.broadcast(in);
            if (in.equals("exit")) {
                appStream.stop(1000);
                break;
            }
        }
    }
}
