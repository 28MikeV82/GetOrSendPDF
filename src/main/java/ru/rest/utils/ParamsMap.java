package ru.rest.utils;

import java.util.HashMap;
import java.util.Map;

public class ParamsMap extends HashMap<String, String> {
    public ParamsMap() {
        super();
    }

    public ParamsMap(Map<String, String> map) {
        super(map);
    }
}