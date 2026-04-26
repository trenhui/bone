package com.bone.tpa.claim.util;

import com.bone.core.util.PkListUtil;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;

public class SqlJoin {
    public static void main(String[] args) {
        String inputPath = "/Users/helei/Documents/saas-doc/dml/disease.sql";
        String outPath = "/Users/helei/Documents/saas-doc/disease-new.sql";
        try {
            File inputFile = new File(inputPath);
            File outputFile = new File(outPath);
            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

            String line;
            List<String> allLine = Lists.newArrayList();
            while ((line = reader.readLine()) != null) {
                allLine.add(line);

            }

            List<List<String>> subListList =  PkListUtil.split(allLine,500);
            for(List<String> subList : subListList){
                subList = subList.stream().filter(t->StringUtils.isNotBlank(t)).collect(Collectors.toList());
                StringBuffer stringBuffer = new StringBuffer();
                for(int i=0;i<subList.size();i++){
                    String sql = subList.get(i);
                    if(StringUtils.isBlank(sql)){
                        continue;
                    }
                    if(i == 0 ){
                        stringBuffer.append(StringUtils.substringBefore(sql,"VALUES"));
                        stringBuffer.append("VALUES ");
                    }
                    String valuesql =   StringUtils.substringBeforeLast(StringUtils.substringAfter(sql,"VALUES "),";");

                    stringBuffer.append(valuesql);
                    stringBuffer.append(",");
                }
                stringBuffer.deleteCharAt(stringBuffer.length()-1);
                stringBuffer.append(";");
                writer.write(stringBuffer.toString());
                writer.newLine();
            }

            reader.close();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
