import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.tencent.wework.Finance;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.Provider;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.*;
import java.util.Base64;
import java.util.Properties;

public class Main {
    static long sdk;

    static String priKey;

    static long ret = 0L;

    static Connection conn;

    static Statement stmt;

    public static void main(String[] args) throws ClassNotFoundException, SQLException, IOException {
        InputStream in = Main.class.getClassLoader().getResource("config.properties").openStream();
        Properties prop = new Properties();
        prop.load(in);
        String corpid= prop.getProperty("corpid");
        String secret= prop.getProperty("secret");
        priKey= prop.getProperty("priKey");
        String jdbc_url= prop.getProperty("jdbc.url");
        String jdbc_user= prop.getProperty("jdbc.user");
        String jdbc_password= prop.getProperty("jdbc.password");

        sdk = Finance.NewSdk();
        Finance.Init(sdk, corpid, secret);
        //priKey = "MIICXQIBAAKBgQCMWah38LkrcjdfFOMT8HGQzQZT2QoOJ+GT1bvBeBTYQL35Mpdv/rn2uCHo5qIgbsmaM/6ok3maavzsw9OXp6ROAYIuHumKFWJgA909M4KMeq7s/E90Ff/qYhVR5SkhHn74LNmiK4tNRlQ2l0QB3uf12LyDyZ63SjD4wc8038F+dQIDAQABAoGABYs+GxzEZ7mvg786uA0poz9iBlpeqg/7ul/5Nmt0oVUvW+JadodQl7UOy9keYtsVdhSjNMv8g/PBcWXL3CP4WrhtBBfxy0PEblzQZBTO/tQmjgbu6+kRNCdl/xSS1L4XHNCb1NBB6V8BMafKtOyLD6pDEwihwII3YWsUOUje+rkCQQCszQXwW+8dDdLNYs8Ch8nZvnlexqzaF7Z0uxHZ36AFcxJVHxyxXEyjxnKP7lEvLG1VCdQ7SC/nzkC4DIRNQnS9AkEAz+zawV2ZO8DlmICFwbip8PogDhNWefk9SW2vHV4wu7WNUJTtJ3C5/JxisrlGdFnVqmoMBZ6t9E7ULRl0NzH4GQJABMJc0ILnsggobymyg+JTh+C8HQUhy5vtlYd1dWe4U44YyiliM+xT3AriKt6oc8EofbgYlU1mrF6835TQrAQRjQJBAJD+9RX/NYqL3BBbH+uV4Tyg0JXAOn/YpTp9eK6cLpPX6XIWSMNGQwy76cAEn/Mnam7qgPOyUlCaYDALHhYXjJECQQCGH6lvnQs5Zhp3Puoja3Lt2+ppkDX0tdMWBR5KSkmb3T+3O81vM285AsXlzfheKjXmx+YBf+yUm/qQFiNuJtYg";
        if (args[0].equals("sync")) {
            //dbOpen(jdbc_url,jdbc_user,jdbc_password);
            try {
                //int begin_seq = selectEndSeq();
                int begin_seq =0;
                syncMsg(begin_seq);
            } catch (IOException e) {
                e.printStackTrace();
            }
           // dbClose();
        } else if (args[0].equals("down_file")) {
            String file_id = args[1];
            String down_url = args[2];
            String indexbuf = "";
            int timeout = 10;
            System.out.println("开始");
            while (true) {
                long media_data = Finance.NewMediaData();
                ret = Finance.GetMediaData(sdk, indexbuf, file_id, "", "", timeout, media_data);
                System.out.println("getmediadata ret:" + ret);
                if (ret != 0L)
                    return;
                System.out.printf("getmediadata outindex len:%d, data_len:%d, is_finis:%d\n", new Object[] { Integer.valueOf(Finance.GetIndexLen(media_data)), Integer.valueOf(Finance.GetDataLen(media_data)), Integer.valueOf(Finance.IsMediaDataFinish(media_data)) });
                try {
                    FileOutputStream outputStream = new FileOutputStream(new File(down_url), true);
                    outputStream.write(Finance.GetData(media_data));
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (Finance.IsMediaDataFinish(media_data) == 1) {
                    Finance.FreeMediaData(media_data);
                    System.out.println(media_data);
                    break;
                }
                indexbuf = Finance.GetOutIndexBuf(media_data);
                Finance.FreeMediaData(media_data);
            }
        } else {
            System.out.print("指令错误");
        }
        Finance.DestroySdk(sdk);
    }

    public static String decrypt(String str, String privateKey) throws Exception {
        Security.addProvider((Provider)new BouncyCastleProvider());
        byte[] inputByte = Base64.getDecoder().decode(str);
        byte[] decoded = Base64.getDecoder().decode(privateKey);
        RSAPrivateKey priKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        Security.addProvider((Provider)new BouncyCastleProvider());
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(2, priKey);
        String outStr = new String(cipher.doFinal(inputByte), "UTF-8");
        outStr = outStr.substring(outStr.length() - 88);
        return outStr;
    }

    public static void insertSQL(int id_value, String insert_value) {
        try {
            PreparedStatement preStmt = conn.prepareStatement("INSERT INTO mgtx_data (`id`,`value`) values (" + id_value + ",?)");
            preStmt.setString(1, insert_value);
            preStmt.executeUpdate();
            preStmt.close();
        } catch (Exception e) {
            System.out.print(e.getMessage());
        }
    }

    public static int selectEndSeq() {
        try {
            ResultSet rs = stmt.executeQuery("select IFNULL(max(id),0) as max from mgtx_data");
            rs.next();
            int max = rs.getInt("max");
            rs.close();
            return max;
        } catch (Exception e) {
            System.out.print(e.getMessage());
            return 0;
        }
    }

    public static void syncMsg(int seq) throws IOException {
        int limit = 10;
        int timeout = 10;
        long slice = Finance.NewSlice();
        ret = Finance.GetChatData(sdk, seq, limit, "", "", timeout, slice);
        JSONObject resJSON = JSON.parseObject(Finance.GetContentFromSlice(slice));
        if (resJSON.getIntValue("code") != 0) {
            System.out.println("错误1:");
            JSON.writeJSONString(System.out, resJSON, new com.alibaba.fastjson.serializer.SerializerFeature[0]);
            System.exit(1);
        }
        JSONArray msgList = resJSON.getJSONArray("chatdata");
        int msgIndex;
        for (msgIndex = 0; msgIndex < msgList.size(); msgIndex++) {
            JSONObject msgOne = JSON.parseObject(msgList.getString(msgIndex));
            String encrypt_chat_msg = msgOne.getString("encrypt_chat_msg");
            String encrypt_random_key = msgOne.getString("encrypt_random_key");
            int msgOneSeq = msgOne.getIntValue("seq");
            try {
                String encrypt_key = decrypt(encrypt_random_key, priKey);
                long msg = Finance.NewSlice();
                ret = Finance.DecryptData(sdk, encrypt_key, encrypt_chat_msg, msg);
                if (ret != 0L) {
                    System.out.println("解密消息错误: " + ret);
                } else {
                    System.out.println("插入消息: " + msgOneSeq);
                    String msgChinese = Finance.GetContentFromSlice(msg);
                    System.out.println("消息:"+msgChinese);
                    //insertSQL(msgOneSeq, msgChinese);
                    Finance.FreeSlice(msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
//        if (msgIndex >= limit)
//            syncMsg(seq + limit);
    }

    public static void dbOpen(String jdbc_url,String user,String password) throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.jdbc.Driver");
        conn = DriverManager.getConnection(jdbc_url, user, password);
        stmt = conn.createStatement();
    }

    public static void dbClose() throws SQLException {
        stmt.close();
        conn.close();
    }
}