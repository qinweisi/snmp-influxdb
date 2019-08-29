package com.demo.snmp;

import com.demo.entity.SnmpData;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TableEvent;
import org.snmp4j.util.TableUtils;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.*;

/**
 * @Description TODO
 * @Author qinweisi
 * @Date 2019/8/28 14:22
 **/
public class SNMPSessionUtil {

    private Snmp snmp;
    private Address targetAddress;
    private String hostComputer;
    private String port;
    private String community;
    private int version;
    private CommunityTarget communityTarget;

    private Map<String, String> dataMap = new HashMap<>();

    public SNMPSessionUtil(String hostComputer, String port, String community, String version) {
        this.hostComputer = hostComputer;
        this.community = community;
        this.port = port;
        if (version.equals("2")) {
            this.version = SnmpConstants.version2c;
        } else {
            System.out.println("版本不对");
        }
        init();
    }

    // 初始化
    public void init() {
        String target = "udp:" + hostComputer + "/" + port;
        targetAddress = GenericAddress.parse(target);
        try {
            TransportMapping transportMapping = new DefaultUdpTransportMapping();
            snmp = new Snmp(transportMapping);
            snmp.listen();
            // 设置权限
            communityTarget = new CommunityTarget();
            communityTarget.setCommunity(new OctetString(community));
            communityTarget.setAddress(targetAddress);
            // 通信不成功重复次数
            communityTarget.setRetries(2);
            // 超时时间
            communityTarget.setTimeout(2 * 1000);
            // 设置版本
            communityTarget.setVersion(version);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 单个获取方式
    public void getSnmpGet(String... oid) throws IOException {
        ResponseEvent responseEvent = null;
        for (int i = 0; i < oid.length; i++) {
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid[i])));
            // 设置请求方式
            pdu.setType(PDU.GET);
            ResponseEvent event = snmp.send(pdu, communityTarget);
            if (null != event) {
                readResponse(event);
            }
        }

    }

    // 对结果进行解析
    public void readResponse(ResponseEvent event) {
        if (null != event && event.getResponse() != null) {
            System.out.println("收到回复，正在解析");
            Vector<VariableBinding> vector = event.getResponse().getVariableBindings();
            for (int i = 0; i < vector.size(); i++) {
                VariableBinding vec = vector.elementAt(i);
                System.out.println(vec);
            }
        } else
            System.out.println("没有收到回复");
    }

    // 遍历请求
    public void snmpWalk2(String oids[]) {
        // 设置TableUtil的工具
        TableUtils utils = new TableUtils(snmp, new DefaultPDUFactory(PDU.GETBULK));
        utils.setMaxNumRowsPerPDU(2);
        OID[] clounmOid = new OID[oids.length];
        for (int i = 0; i < oids.length; i++) {
            clounmOid[i] = new OID(oids[i]);
        }
        // 获取查询结果list,new OID("0"),new OID("40")设置输出的端口数量
        List<TableEvent> list = utils.getTable(communityTarget, clounmOid, new OID("0"), new OID("40"));
        Date sysTime = new Date();
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
                SnmpData data = new SnmpData();
                data.setPort(vb[1].toString());
                String key = vb[1].toString().split("=")[0].trim();
                if (dataMap.containsKey(key + "-in")) {
                    String in = (Long.parseLong(vb[3].toString().split("=")[1].trim()) - Long.parseLong(dataMap.get(key + "-in")))+ "";
                    String out = (Long.parseLong(vb[4].toString().split("=")[1].trim()) - Long.parseLong(dataMap.get(key + "-out")))+ "";
                    data.setIn(in);
                    data.setOut(out);
//                    data.setTime(sysTime);
                    entity.add(data);
                } else {
                    dataMap.put(key + "-in", vb[3].toString().split("=")[1].trim());
                    dataMap.put(key + "-out", vb[4].toString().split("=")[1].trim());
                }
                System.out.println(vb[1]);
            } else {
                throw new NullPointerException("被监控系统的网络不通或IP或其它相关配置错识！");
            }
        }
    }

    public Map<String,Object> test(String oids[]) {
        // 设置TableUtil的工具
        TableUtils utils = new TableUtils(snmp, new DefaultPDUFactory(PDU.GETBULK));
        utils.setMaxNumRowsPerPDU(2);
        OID[] clounmOid = new OID[oids.length];
        for (int i = 0; i < oids.length; i++) {
            clounmOid[i] = new OID(oids[i]);
        }
        // 获取查询结果list,new OID("0"),new OID("40")设置输出的端口数量
        List<TableEvent> list = utils.getTable(communityTarget, clounmOid, new OID("0"), new OID("40"));
        String sysTime = String.valueOf(System.currentTimeMillis());
        Map<String, Object> result = new HashMap<>();
        result.put("list",list);
        result.put("time",sysTime);
        return result;
    }

    // 测试
    public static void main(String[] args) {
        SNMPSessionUtil snmpSessionUtil = new SNMPSessionUtil("127.0.0.1", "161", "public", "2");
        try {
            snmpSessionUtil.getSnmpGet(Constants.sysDescr, Constants.sysName, Constants.sysObjectID, Constants.ifNumber);
            snmpSessionUtil.snmpWalk2(Constants.ifOids);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

