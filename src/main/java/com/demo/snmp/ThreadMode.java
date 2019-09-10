package com.demo.snmp;

import com.demo.entity.SnmpData;
import com.demo.job.GetData;
import com.demo.util.InfluxDao;
import com.demo.util.SpringContextUtils;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.util.TableEvent;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Description TODO
 * @Author qinweisi
 * @Date 2019/8/29 16:49
 **/
public class ThreadMode {

    @Autowired
    private InfluxDao dao;

    public Thread getThread(Map<String, String> map) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String ip = map.get("ip");
                SNMPSessionUtil snmpSessionUtil = new SNMPSessionUtil(ip, map.get("port"), map.get("community"), map.get("version"));
                Map<String, Object> data = snmpSessionUtil.test(Constants.ifOids);
                if (data != null) {
                    List<TableEvent> list = (List<TableEvent>) data.get("list");
                    String time = data.get("time").toString();
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
                            snmpData.setIp(ip);
                            snmpData.setPort(vb[1].toString().split("=")[1].trim());
                            String key = ip + "-" + vb[1].toString().split("=")[0].trim();
//                            if (!GetData.flagMap.containsKey(ip)) {
//                                GetData.dataMap.put(key + "-in", vb[3].toString().split("=")[1].trim());
//                                GetData.dataMap.put(key + "-out", vb[4].toString().split("=")[1].trim());
//                                continue;
//                            }
                            String LastIn = GetData.dataMap.get(key + "-in");
                            String LastOut = GetData.dataMap.get(key + "-out");
                            String nowIn = vb[3].toString().split("=")[1].trim();
                            String nowOut = vb[4].toString().split("=")[1].trim();
                            if (GetData.dataMap.containsKey(key + "-in")) {
                                String in = (Long.parseLong(nowIn) - Long.parseLong(LastIn)) + "";
                                String out = (Long.parseLong(nowOut) - Long.parseLong(LastOut)) + "";
                                snmpData.setIn(in);
                                snmpData.setOut(out);
                                snmpData.setSysTime(time);
                                snmpData.setLastIn(LastIn);
                                snmpData.setLastOut(LastOut);
                                snmpData.setNowIn(nowIn);
                                snmpData.setNowOut(nowOut);
                                entity.add(snmpData);
                            } else {
                                snmpData.setIn(nowIn);
                                snmpData.setOut(nowOut);
                                snmpData.setSysTime(time);
                                snmpData.setLastIn("0");
                                snmpData.setLastOut("0");
                                snmpData.setNowIn(nowIn);
                                snmpData.setNowOut(nowOut);
                            }
                            // 第一次查询不需要计算/计算完成后覆盖原来的值
                            GetData.dataMap.put(key + "-in", vb[3].toString().split("=")[1].trim());
                            GetData.dataMap.put(key + "-out", vb[4].toString().split("=")[1].trim());
                            System.out.println(vb[1]);
                        } else {
                            throw new NullPointerException("被监控系统的网络不通或IP或其它相关配置错识！");
                        }
                    }
                    if (entity.size() > 0) {
                        InfluxDao influxDao = SpringContextUtils.getBean(InfluxDao.class);
                        influxDao.insert(entity);
                    }
                    System.out.println(time);
                }
            }
        });
        return thread;
    }
}
