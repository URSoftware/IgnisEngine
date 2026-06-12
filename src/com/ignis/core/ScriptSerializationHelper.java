package com.ignis.core;

import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Decoupled helper class to manage script field serialization, deserialization,
 * and exposure rules using reflection and the @Serialize annotation.
 */
public final class ScriptSerializationHelper {

    private ScriptSerializationHelper() {
    }

    /**
     * Interface to resolve GameObject references by name.
     */
    public interface GameObjectResolver {
        GameObject resolve(String name);
    }

    /**
     * Gets all serializable fields of a script class.
     * Only fields marked with @Serialize are returned.
     * Traverses class hierarchy up to but excluding IgnisScript.
     */
    public static List<Field> getSerializedFields(Class<?> clazz) {
        List<Field> fieldList = new ArrayList<>();
        Class<?> current = clazz;

        while (current != null && current != IgnisScript.class && IgnisScript.class.isAssignableFrom(current)) {
            Field[] declaredFields = current.getDeclaredFields();
            for (Field field : declaredFields) {
                // Ignore static fields
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                
                // Only serialize fields marked with @Serialize
                if (field.isAnnotationPresent(Serialize.class)) {
                    field.setAccessible(true);
                    fieldList.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fieldList;
    }

    /**
     * Checks if a field type is supported for serialization.
     */
    public static boolean isSupportedType(Class<?> type) {
        return type == int.class || type == Integer.class ||
               type == double.class || type == Double.class ||
               type == float.class || type == Float.class ||
               type == long.class || type == Long.class ||
               type == boolean.class || type == Boolean.class ||
               type == String.class ||
               GameObject.class.isAssignableFrom(type);
    }

    /**
     * Serializes all annotated fields of a script to JSON.
     */
    public static JSONObject saveScriptVariables(IgnisScript script) {
        JSONObject variables = new JSONObject();
        try {
            List<Field> fields = getSerializedFields(script.getClass());
            for (Field field : fields) {
                Class<?> type = field.getType();
                if (!isSupportedType(type)) continue;

                Object value = field.get(script);
                if (value == null) continue;

                if (type == int.class || type == Integer.class ||
                    type == double.class || type == Double.class ||
                    type == float.class || type == Float.class ||
                    type == long.class || type == Long.class ||
                    type == boolean.class || type == Boolean.class ||
                    type == String.class) {
                    variables.put(field.getName(), value);
                } else if (GameObject.class.isAssignableFrom(type)) {
                    GameObject ref = (GameObject) value;
                    JSONObject refData = new JSONObject();
                    refData.put("_type", "GameObjectRef");
                    refData.put("name", ref.getName());
                    variables.put(field.getName(), refData);
                }
            }
        } catch (Exception e) {
            System.err.println("Error serializing script variables: " + e.getMessage());
        }
        return variables;
    }

    /**
     * Loads script variables from JSON into the script class, using a resolver for game objects.
     */
    public static void loadScriptVariables(IgnisScript script, JSONObject variables, GameObjectResolver resolver) {
        try {
            Class<?> clazz = script.getClass();
            for (String fieldName : variables.keySet()) {
                try {
                    // Traverse hierarchy to find the field
                    Field field = findFieldInHierarchy(clazz, fieldName);
                    if (field == null) continue;

                    // Ensure the field is annotated with @Serialize
                    if (!field.isAnnotationPresent(Serialize.class)) {
                        System.out.println("[Serialization Warning] Field '" + fieldName + "' in script '" 
                                + clazz.getSimpleName() + "' was loaded from JSON but is not annotated with @Serialize. Ignoring.");
                        continue;
                    }

                    field.setAccessible(true);
                    Class<?> type = field.getType();
                    Object value = variables.get(fieldName);

                    if (value instanceof JSONObject) {
                        JSONObject refData = (JSONObject) value;
                        if (refData.has("_type") && "GameObjectRef".equals(refData.getString("_type"))) {
                            String refName = refData.getString("name");
                            GameObject referenced = resolver.resolve(refName);
                            if (referenced != null) {
                                field.set(script, referenced);
                            }
                        }
                    } else if (type == int.class || type == Integer.class) {
                        field.set(script, variables.getInt(fieldName));
                    } else if (type == double.class || type == Double.class) {
                        field.set(script, variables.getDouble(fieldName));
                    } else if (type == float.class || type == Float.class) {
                        field.set(script, (float) variables.getDouble(fieldName));
                    } else if (type == long.class || type == Long.class) {
                        field.set(script, variables.getLong(fieldName));
                    } else if (type == boolean.class || type == Boolean.class) {
                        field.set(script, variables.getBoolean(fieldName));
                    } else if (type == String.class) {
                        field.set(script, variables.getString(fieldName));
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to load field " + fieldName + ": " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading script variables: " + e.getMessage());
        }
    }

    private static Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != IgnisScript.class && IgnisScript.class.isAssignableFrom(current)) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
