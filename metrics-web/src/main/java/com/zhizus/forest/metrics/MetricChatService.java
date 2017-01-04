package com.zhizus.forest.metrics;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import com.zhizus.forest.metrics.client.Metrics;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by Dempe on 2017/1/4.
 */
@Service
public class MetricChatService {


    @Autowired
    private MetricsDao metricsDao;


    private Map<Integer, Integer> initTimeDistributionMap() {
        Map<Integer, Integer> map = Maps.newTreeMap();
        for (Integer region : initRegionList()) {
            map.put(region, 0);
        }
        return map;
    }

    public List<Integer> initRegionList() {
        return Lists.newArrayList(10, 20, 30, 50, 100, 200, 300, 500, 800, 1000, 2000, 5000);
    }

    public void incRegionMap(Map<Integer, Integer> regionMap, int value) {
        Integer regionKey = getRegionKey(value);
        Integer count = regionMap.get(regionKey);
        if (count == null) {
            count = 0;
            regionMap.put(regionKey, count);
        }
        regionMap.put(regionKey, ++count);
    }

    private Integer getRegionKey(int value) {
        List<Integer> regionList = initRegionList();
        for (Integer region : regionList) {
            if (value < region) {
                return region;
            }
        }
        return regionList.get(regionList.size() - 1);
    }

    private JSONArray wrapArray(Object xAxis, Object yAxis) {
        JSONArray array = new JSONArray();
        array.add(xAxis);
        array.add(yAxis);
        return array;
    }

    public JSONObject wrapChatData(AggregateIterable<Document> documents) {
        JSONObject result = new JSONObject();

        JSONArray countArr = new JSONArray();
        JSONArray errCountArr = new JSONArray();
        JSONArray minTimeArr = new JSONArray();
        JSONArray maxTimeArr = new JSONArray();
        JSONArray avgTimeArr = new JSONArray();
        Map<Integer, Integer> regionMap = initTimeDistributionMap();
        Map<Integer, Integer> codeMap = Maps.newHashMap();

        MongoCursor<Document> iterator = documents.iterator();
        while (iterator.hasNext()) {
            Document document = iterator.next();

            Long xAxis = document.getLong(MetricsDao.MetricField.ID.getName());

            Integer maxTime = document.getInteger(MetricsDao.MetricField.MAX_TIME.getName());
            Integer minTime = document.getInteger(MetricsDao.MetricField.MIN_TIME.getName());
            Integer count = document.getInteger(MetricsDao.MetricField.COUNT.getName());

            Double timeDouble = Double.valueOf(document.getLong(MetricsDao.MetricField.TIME.getName()));
            Double countDouble = Double.valueOf(count);
            Double avgTime = timeDouble == null || countDouble == null || countDouble == 0 ? 0D : (timeDouble / countDouble);

            List<List<Integer>> codes = (List<List<Integer>>) (document.get("array"));
            gatherCodes(codeMap, codes);

            int errCount = 0;
            if (codes != null && count != null) {
                errCount = codeMap.get(0);
            }
            countArr.add(wrapArray(xAxis, count));
            errCountArr.add(wrapArray(xAxis, errCount));

            // maxTime
            maxTimeArr.add(wrapArray(xAxis, maxTime));
            // minTime
            minTimeArr.add(wrapArray(xAxis, minTime));
            // avgTime
            avgTimeArr.add(wrapArray(xAxis, avgTime));

            incRegionMap(regionMap, avgTime.intValue());

        }


        // 请求数
        JSONArray countSeries = new JSONArray();
        addSeries(countSeries, "count", countArr);
        addSeries(countSeries, "errCount", errCountArr, false);

        // 时延
        JSONArray timeSeries = new JSONArray();
        addSeries(timeSeries, "avgTime", avgTimeArr);
        addSeries(timeSeries, "maxTime", maxTimeArr, false);
        addSeries(timeSeries, "minTime", minTimeArr, false);


        // 时延分布
        JSONArray timeDis = mapToColumnData(regionMap);
        JSONArray timeDisSeries = new JSONArray();
        addSeries(timeDisSeries, "time", timeDis);

        //
        JSONArray pieCodes = mapToPieData(codeMap);
        JSONArray codeSeries = new JSONArray();
        addSeries(codeSeries, "codes", pieCodes);


        result.put("count", countSeries);
        result.put("time", timeSeries);
        result.put("timeDistribution", timeDisSeries);
        result.put("codes", codeSeries);
        return result;
    }

    public JSONObject groupByXAxis(String serviceName, String uri, String ip, String roomId, String version, String type) {
        AggregateIterable<Document> documents = metricsDao.groupByXAxis(serviceName, uri, ip, roomId, version, type);
        return wrapChatData(documents);
    }

    public void gatherCodes(Map<Integer, Integer> map, List<List<Integer>> codes) {
        for (List<Integer> code : codes) {
            for (int i = 0; i < code.size(); i++) {

                Integer value = map.get(i);
                if (value == null) {
                    value = 0;
                }
                value = value + code.get(i);
                map.put(i, value);
            }
        }
    }

    private void addSeries(JSONArray series, String name, JSONArray data) {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("data", data);
        json.put("visible", true);
        series.add(json);
    }

    private void addSeries(JSONArray series, String name, JSONArray data, boolean visible) {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("data", data);
        json.put("visible", visible);
        series.add(json);
    }

    public JSONArray mapToColumnData(Map map) {
        JSONArray columnData = new JSONArray();
        Iterator<Map.Entry<Object, Object>> mapIterator = map.entrySet().iterator();
        while (mapIterator.hasNext()) {
            Map.Entry<Object, Object> next = mapIterator.next();
            JSONObject json = new JSONObject();
            json.put("name", next.getKey());
            json.put("y", next.getValue());
            columnData.add(json);
        }
        return columnData;
    }

    public JSONArray mapToPieData(Map<?, Integer> map) {
        Iterator<Integer> iterator = map.values().iterator();
        int total = 0;
        while (iterator.hasNext()) {
            total += iterator.next();
        }
        JSONArray pieData = new JSONArray();

        Iterator<? extends Map.Entry<?, Integer>> mapIterator = map.entrySet().iterator();
        while (mapIterator.hasNext()) {
            Map.Entry<?, Integer> next = mapIterator.next();
            JSONObject json = new JSONObject();
            json.put("name", next.getKey());
            json.put("y", next.getValue() * 100 / total);
            pieData.add(json);
        }
        JSONArray result = new JSONArray();
        for (int i = 0; i < pieData.size(); i++) {
            JSONObject json = pieData.getJSONObject(i);
            if (json.getIntValue("y") > 0) {
                Metrics.Status status = Metrics.Status.getStatus(i);
                json.put("name", status.name());
                result.add(json);
            }
        }
        return result;
    }


}
