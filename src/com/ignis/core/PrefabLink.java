package com.ignis.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents the linkage between a GameObject instance and its source Prefab template.
 * Tracks property overrides made locally on the instance.
 */
public class PrefabLink {

    private String prefabName;
    private final Set<String> overriddenProperties = new HashSet<>();

    public PrefabLink(String prefabName) {
        if (prefabName == null || prefabName.trim().isEmpty()) {
            throw new IllegalArgumentException("prefabName cannot be null or empty");
        }
        this.prefabName = prefabName.trim();
    }

    public String getPrefabName() {
        return prefabName;
    }

    public void setPrefabName(String prefabName) {
        if (prefabName != null && !prefabName.trim().isEmpty()) {
            this.prefabName = prefabName.trim();
        }
    }

    public boolean isOverridden(String propertyName) {
        if (propertyName == null) return false;
        return overriddenProperties.contains(propertyName);
    }

    public void setOverride(String propertyName) {
        if (propertyName != null && !propertyName.trim().isEmpty()) {
            overriddenProperties.add(propertyName.trim());
        }
    }

    public void removeOverride(String propertyName) {
        if (propertyName != null) {
            overriddenProperties.remove(propertyName.trim());
        }
    }

    public void clearAllOverrides() {
        overriddenProperties.clear();
    }

    public Set<String> getOverriddenProperties() {
        return Collections.unmodifiableSet(overriddenProperties);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("prefabName", prefabName);
        JSONArray overridesArray = new JSONArray();
        for (String prop : overriddenProperties) {
            overridesArray.put(prop);
        }
        json.put("overriddenProperties", overridesArray);
        return json;
    }

    public static PrefabLink fromJson(JSONObject json) {
        if (json == null || !json.has("prefabName")) {
            return null;
        }
        String name = json.getString("prefabName");
        PrefabLink link = new PrefabLink(name);
        if (json.has("overriddenProperties")) {
            JSONArray arr = json.getJSONArray("overriddenProperties");
            for (int i = 0; i < arr.length(); i++) {
                link.setOverride(arr.getString(i));
            }
        }
        return link;
    }
}
