package com.demo.job;

import com.demo.entity.SnmpData;
import com.demo.snmp.Constants;
import com.demo.snmp.SNMPSessionUtil;
import com.demo.snmp.ThreadMode;
import com.demo.util.InfluxDao;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.util.TableEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description TODO
 * @Author qinweisi
 * @Date 2019/8/28 15:14
 **/
@Component
public class GetData {

    public static Map<String, String> dataMap = new HashMap<>();
    public static Map<String, String> flagMap = new HashMap<>();

    @Autowired
    public static InfluxDao dao;

//    @Scheduled(cron = "0/15 * * * * ?")
    public void getData() {
        SNMPSessionUtil snmpSessionUtil = new SNMPSessionUtil("192.168.0.1", "161", "Anonymous", "2");

        Map<String, Object> data = snmpSessionUtil.test(Constants.ifOids);
        if (data != null) {
            List<TableEvent> list = (List<TableEvent>) data.get("list");
            String time =  data.get("time").toString();
            ArrayList<SnmpData> entity = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                // 取list中的一行
                TableEvent te = (TableEvent) list.get(i);
                // 对每一行结果进行再次拆分
                VariableBinding[] vb = te.getColumns();
                if (!vb[1].toString().contains("GigabitEthernet")) {
                    continue;
                }
                if (vb != null) {
                    SnmpData snmpData = new SnmpData();
                    snmpData.setPort(vb[1].toString().split("=")[1].trim());
                    String key = vb[1].toString().split("=")[0].trim();
                    if (dataMap.containsKey(key + "-in")) {
                        String LastIn = dataMap.get(key + "-in");
                        String LastOut = dataMap.get(key + "-out");
                        String nowIn = vb[3].toString().split("=")[1].trim();
                        String nowOut = vb[4].toString().split("=")[1].trim();
                        String in = (Long.parseLong(nowIn) - Long.parseLong(LastIn))+ "";
                        String out = (Long.parseLong(nowOut) - Long.parseLong(LastOut))+ "";
                        snmpData.setIn(in);
                        snmpData.setOut(out);
                        snmpData.setSysTime(time);
                        snmpData.setLastIn(LastIn);
                        snmpData.setLastOut(LastOut);
                        snmpData.setNowIn(nowIn);
                        snmpData.setNowOut(nowOut);
                        entity.add(snmpData);
                    }
                    // 第一次查询不需要计算/计算完成后覆盖原来的值
                    dataMap.put(key + "-in", vb[3].toString().split("=")[1].trim());
                    dataMap.put(key + "-out", vb[4].toString().split("=")[1].trim());
                    System.out.println(vb[1]);
                } else {
                    throw new NullPointerException("被监控系统的网络不通或IP或其它相关配置错识！");
                }
            }
            if(entity.size() > 0){
//                dao.insert(entity);
            }
            System.out.println(time);
        }
    }

//    @Scheduled(cron = "0/15 * * * * ?")
    public void timeAndPackage() {
        String shell = "/root/ping/pingtool.sh";
        try {
            Runtime rt = Runtime.getRuntime();
            rt.exec(shell);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Scheduled(cron = "0/15 * * * * ?")
    public void getData1() {
        List<Map<String,String>> list = new ArrayList<>();
//        Map<String,String> map = new HashMap<>();
//        map.put("ip","127.0.0.1");
//        map.put("port","161");
//        map.put("community","public");
//        map.put("version","2");
//        list.add(map);
        Map<String,String> map1 = new HashMap<>();
        map1.put("ip","192.168.0.1");
        map1.put("port","161");
        map1.put("community","Anonymous");
        map1.put("version","2");
        list.add(map1);
        //线程数量
        int threadmax = list.size();
        for (int i = 0; i < threadmax; i++) {
            ThreadMode thread = new ThreadMode();
            thread.getThread(list.get(i)).start();
        }
    }
}
