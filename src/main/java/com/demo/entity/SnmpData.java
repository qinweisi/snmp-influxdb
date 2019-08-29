package com.demo.entity;

import com.demo.util.Tag;
import lombok.Data;
import org.influxdb.annotation.Measurement;

import java.util.Date;

/**
 * @Description 实体
 * @Author qinweisi
 * @Date 2019/8/28 15:22
 **/
@Data
@Measurement(name = "snmpData")// 表名
public class SnmpData {

    @Tag
    private String sysTime;
    private String port;
    private String in;
    private String out;
    private String lastIn;
    private String lastOut;
    private String nowIn;
    private String nowOut;

}
