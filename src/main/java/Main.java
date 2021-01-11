import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Main
{
    private static Elements metrodatadiv;
    private static JSONObject mainObj; // contains writed json file
    private static JSONObject stationsObj; // contains JSONArray
    private static JSONArray linesObj; // contains JSONObject
    private static ArrayList<String> lineNumbers;
    private static JSONArray connectionObj; // contains JSONObject

    public static void main(String[] args)
    {
        metrodatadiv = getMetroDataDiv();
        mainObj = new JSONObject();
        stationsObj = new JSONObject();
        linesObj = new JSONArray();
        lineNumbers = getLineNumbers();
        connectionObj = new JSONArray();
        fillStationsObj();
        fillLinesObj();
        fillConnectionObj();
        fillMainObj();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(new File("data/map.json"), mainObj);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Elements getMetroDataDiv()
    {
        String url = "https://www.moscowmap.ru/metro.html#lines";
        Elements elements = null;
        try {
            Document document = Jsoup.connect(url).maxBodySize(0).get();
            elements =  document.select("div#metrodata");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return elements;
    }

    private static ArrayList<String> getLineNumbers()
    {
        ArrayList<String> lines = new ArrayList<>();
        Elements spans = metrodatadiv.select("span");
        for (Element span : spans) {
            if (!span.attr("data-line").equals("")) {
                lines.add(span.attr("data-line"));
            }
        }
        return lines;
    }

    private static String getLineNameByNumber(String number)
    {
        return metrodatadiv.select("span[data-line=" + number + "]").text();
    }

    private static Elements getStationsByLineNumber(String lineNumber)
    {

        return metrodatadiv.select("div[data-line=" + lineNumber + "]");
    }

    private static Elements getTransitionsFromStation(Element station)
    {
        return station.select("span[title^=переход]");
    }

    private static String getTransitionStationName(String transitionStationTitle)
    {
        int beginIndex = transitionStationTitle.indexOf("«") + 1;
        int endIndex = transitionStationTitle.indexOf("»");
        return transitionStationTitle.substring(beginIndex, endIndex);
    }

    private static HashSet<String> getLineNumbersByStationName(String name)
    {
        HashSet<String> numbers = new HashSet<>();
        for (Object key : stationsObj.keySet()) {
            if (stationsObj.get(key).toString().contains(name) & !numbers.contains(key.toString())) {
                numbers.add((String) key);
            }
        }
        return numbers;
    }

    private static HashMap<String, String> getStationInfoMap(String name, String number)
    {
        HashMap<String, String> stationInfo = new HashMap<>();
        stationInfo.put("line", number);
        stationInfo.put("name", name);
        return stationInfo;
    }

    private static void addToStationList(JSONArray list,
                                         String name,
                                         String number) {
        HashMap<String, String> infoMap = getStationInfoMap(name, number);
        if (!list.contains(infoMap)) {
            list.add(infoMap);
        }
    }

    private static void fillStationsObj()
    {
        for (String number : lineNumbers) {
            JSONArray stationsList = new JSONArray();
            getStationsByLineNumber(number).forEach(stations -> {
                stations.select("p").forEach(station -> {
                    stationsList.add(station.select("span.name").text());
                });
            });
            stationsObj.put(number, stationsList);
        }
    }

    private static void fillLinesObj()
    {
        for (String number : lineNumbers) {
            JSONObject lineInfo = new JSONObject();
            lineInfo.put("number", number);
            lineInfo.put("name", getLineNameByNumber(number));
            linesObj.add(lineInfo);
        }
    }

    private static void fillConnectionObj()
    {
        for (String number : lineNumbers) {
            getStationsByLineNumber(number).forEach(stations -> {
                stations.select("p").forEach(station -> {
                    Elements transitions = getTransitionsFromStation(station);
                    if (!transitions.isEmpty()) {
                        JSONArray connectedStationsList = new JSONArray();
                        transitions.forEach(transition -> {
                            String transitionTitle = transition.attr("title");
                            String stationName = getTransitionStationName(transitionTitle);
                            for (String stationNumber : getLineNumbersByStationName(stationName)) {
                                addToStationList(connectedStationsList, stationName, stationNumber);
                            }
                        });
                        String currentStationName = station.select("span.name").text();
                        for (String stationNumber : getLineNumbersByStationName(currentStationName)){
                            addToStationList(connectedStationsList, currentStationName, stationNumber);
                        }
                        connectionObj.add(connectedStationsList);
                    }
                });
            });
        }
    }



    private static void fillMainObj()
    {
        mainObj.put("stations", stationsObj);
        mainObj.put("lines", linesObj);
        mainObj.put("connections", connectionObj);
    }
}
