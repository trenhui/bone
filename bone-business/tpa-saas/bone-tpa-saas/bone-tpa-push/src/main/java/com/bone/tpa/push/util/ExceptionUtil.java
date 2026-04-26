package com.bone.tpa.push.util;

import lombok.SneakyThrows;

import java.io.*;

public class ExceptionUtil {
    @SneakyThrows
    public static String getExceptionString(Exception e) {
        //读取异常堆栈信息
        ByteArrayOutputStream arrayOutputStream=new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(arrayOutputStream));
        //通过字节数组转换输入输出流
        BufferedReader fr=new BufferedReader(new InputStreamReader(new ByteArrayInputStream(arrayOutputStream.toByteArray())));
        String str;
        StringBuilder exceptionStr=new StringBuilder();
        while ((str=fr.readLine())!=null){
            exceptionStr.append(str);
        }
        //一定一定要关闭流
        fr.close();
        String msg = exceptionStr.toString();
        if (msg.length() > 1024) {
            msg = msg.substring(0, 1024);
        }
        return msg;
    }
}
