package com.demo.util;

import lombok.extern.slf4j.Slf4j;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDB.ConsistencyLevel;
import org.influxdb.annotation.Measurement;
import org.influxdb.dto.*;
import org.influxdb.dto.Point.Builder;
import org.influxdb.dto.QueryResult.Result;
import org.influxdb.dto.QueryResult.Series;

import org.nutz.json.Json;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import org.nutz.lang.Lang;

@Component
@Slf4j
public class InfluxDaoImpl implements InfluxDao {

    @Autowired
    InfluxDB influxDB;
    @Value("${spring.influx.database:''}")
    private String database;

    @Override
    public Boolean ping() {
        boolean isConnected = false;
        Pong pong;
        try {
            pong = influxDB.ping();
            if (pong != null) {
                isConnected = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isConnected;
    }



    @Override
    public void deleteDataBase(String... dataBaseName) {
        if (dataBaseName.length > 0) {
            influxDB.deleteDatabase(dataBaseName[0]);
            return;
        }
        if (database == null) {
//            log.error("如参数不指定数据库名,配置文件 spring.influx.dataBaseName 必须指定");
            return;
        }
        influxDB.deleteDatabase(database);
    }

    @Override
    public <T> void insert(T object) {
        // 构建一个Entity
        Object first = Lang.first(object);
        Class clazz = first.getClass();
        // 表名
        Boolean isAnnot = clazz.isAnnotationPresent(Measurement.class);
        if (!isAnnot) {
//            log.error("插入的数据对应实体类需要@Measurement注解");
            return;
        }
        Measurement annotation = (Measurement) clazz.getAnnotation(Measurement.class);
        // 表名
        String measurement = annotation.name();
        Field[] arrfield = clazz.getDeclaredFields();
        // 数据长度
        int size = Lang.eleSize(object);
        String tagField = ReflectUtils.getField(object, Tag.class);
        if (tagField == null) {
//            log.error("插入多条数据需对应实体类字段有@Tag注解");
            return;
        }
        BatchPoints batchPoints = BatchPoints
                .database(database)
                // 一致性
                .consistency(ConsistencyLevel.ALL)
                .build();
        for (int i = 0; i < size; i++) {
            Map<String, Object> map = new HashMap<>();
            Builder builder = Point.measurement(measurement);
            for (Field field : arrfield) {
                // 私有属性需要开启
                field.setAccessible(true);
                Object result = first;
                try {
                    if (size > 1) {
                        List objects = (List) (object);
                        result = objects.get(i);
                    }
                    if (field.getName().equals(tagField)) {
                        builder.tag(tagField, field.get(result).toString());
                    } else {
                        map.put(field.getName(), field.get(result));
                    }
                } catch (IllegalAccessException e) {
//                    log.error("实体转换出错");
                    e.printStackTrace();
                }
            }
            builder.fields(map);
            batchPoints.point(builder.build());
            influxDB.write(batchPoints);
        }
    }

    @Override
    public <T> List<T> query(Class<T> clazz, String sql) {
        if (database == null) {
//            log.error("查询数据时配置文件 spring.influx.dataBaseName 必须指定");
            return null;
        }
        QueryResult results = influxDB.query(new Query(sql, database));
        if (results != null) {
            if (results.getResults() == null) {
                return null;
            }
            List<Object> list = new ArrayList<>();

            for (Result result : results.getResults()) {
                List<Series> series = result.getSeries();
                if (series == null) {
                    list.add(null);
                    continue;
                }
                for (Series serie : series) {
                    List<List<Object>> values = serie.getValues();
                    List<String> columns = serie.getColumns();
                    // 构建Bean
                    list.addAll(getQueryData(clazz, columns, values));
                }
            }
            return Json.fromJsonAsList(clazz, Json.toJson(list));
        }
        return null;
    }

    /**
     * 自动转换对应Pojo
     *
     * @param values
     * @return
     */
    public <T> List<T> getQueryData(Class<T> clazz, List<String> columns, List<List<Object>> values) {
        List results = new ArrayList<>();
        for (List<Object> list : values) {
            BeanWrapperImpl bean = null;
            Object result = null;
            try {
                result = clazz.newInstance();
                bean = new BeanWrapperImpl(result);
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            for (int i = 0; i < list.size(); i++) {
                // 字段名
                String filedName = columns.get(i);
                if (filedName.equals("Tag")) {
                    continue;
                }
                try {
                    Field field = clazz.getDeclaredField(filedName);
                } catch (NoSuchFieldException e) {
                    continue;
                }
                // 值
                Object value = list.get(i);
                bean.setPropertyValue(filedName, value);
            }
            results.add(result);
        }
        return results;
    }
}
