package org.example.miniodemo;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

public class MD5Util {
    public static String calculateMD5(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        // 创建 MD5 摘要算法的实例
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        
        // 创建缓冲区来读取输入流
        byte[] buffer = new byte[1024];
        int bytesRead;

        // 逐块读取流并更新消息摘要
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            messageDigest.update(buffer, 0, bytesRead);
        }

        // 完成哈希计算
        byte[] md5Bytes = messageDigest.digest();

        // 将 MD5 字节数组转换为十六进制字符串
        StringBuilder md5Hex = new StringBuilder();
        for (byte b : md5Bytes) {
            md5Hex.append(String.format("%02x", b));
        }

        return md5Hex.toString();
    }
}
