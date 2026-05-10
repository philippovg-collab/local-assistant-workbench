package com.example.demo.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

public final class ApiContractSchemaGenerator {

    private static final String MODEL_PACKAGE = "com.example.demo.model";

    private final ObjectMapper objectMapper;

    public ApiContractSchemaGenerator() {
        this.objectMapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public Map<String, Object> generate(Collection<Class<?>> rootTypes) {
        Queue<Class<?>> pending = new ArrayDeque<>(rootTypes);
        Map<String, Map<String, Object>> types = new TreeMap<>();

        while (!pending.isEmpty()) {
            Class<?> type = pending.remove();
            if (!isContractType(type) || types.containsKey(contractName(type))) {
                continue;
            }

            if (type.isEnum()) {
                types.put(contractName(type), enumDescriptor(type));
            } else if (type.isRecord()) {
                types.put(contractName(type), recordDescriptor(type, pending));
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("schemaVersion", 1);
        schema.put("types", types);
        return schema;
    }

    private Map<String, Object> enumDescriptor(Class<?> type) {
        Map<String, Object> descriptor = baseDescriptor("enum", type);
        List<Object> values = new ArrayList<>();
        for (Object constant : type.getEnumConstants()) {
            values.add(jsonValueOf(constant));
        }
        descriptor.put("values", values);
        return descriptor;
    }

    private Map<String, Object> recordDescriptor(Class<?> type, Queue<Class<?>> pending) {
        Map<String, Object> descriptor = baseDescriptor("record", type);
        List<Map<String, Object>> fields = new ArrayList<>();
        for (RecordComponent component : type.getRecordComponents()) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", jsonPropertyName(component));
            field.put("javaName", component.getName());
            field.put("type", typeDescriptor(component.getGenericType(), pending));
            field.put("nullable", !component.getType().isPrimitive());
            fields.add(field);
        }
        fields.sort(Comparator.comparing(field -> field.get("name").toString()));
        descriptor.put("fields", fields);
        return descriptor;
    }

    private Map<String, Object> baseDescriptor(String kind, Class<?> type) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("kind", kind);
        descriptor.put("javaType", type.getName());
        return descriptor;
    }

    private Map<String, Object> typeDescriptor(Type type, Queue<Class<?>> pending) {
        if (type instanceof Class<?> rawClass) {
            return classDescriptor(rawClass, pending);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return parameterizedDescriptor(parameterizedType, pending);
        }
        if (type instanceof GenericArrayType arrayType) {
            return arrayDescriptor(typeDescriptor(arrayType.getGenericComponentType(), pending));
        }
        if (type instanceof WildcardType wildcardType) {
            Type[] upperBounds = wildcardType.getUpperBounds();
            return upperBounds.length == 0 ? unknownDescriptor(type) : typeDescriptor(upperBounds[0], pending);
        }
        return unknownDescriptor(type);
    }

    private Map<String, Object> classDescriptor(Class<?> rawClass, Queue<Class<?>> pending) {
        if (rawClass.isArray()) {
            return arrayDescriptor(typeDescriptor(rawClass.getComponentType(), pending));
        }
        if (rawClass == String.class || rawClass == Character.class || rawClass == char.class) {
            return scalarDescriptor("string");
        }
        if (rawClass == boolean.class || rawClass == Boolean.class) {
            return scalarDescriptor("boolean");
        }
        if (rawClass == byte.class || rawClass == short.class || rawClass == int.class || rawClass == long.class
            || rawClass == Byte.class || rawClass == Short.class || rawClass == Integer.class || rawClass == Long.class
            || rawClass == BigInteger.class) {
            return scalarDescriptor("integer");
        }
        if (rawClass == float.class || rawClass == double.class || rawClass == Float.class || rawClass == Double.class
            || rawClass == BigDecimal.class) {
            return scalarDescriptor("number");
        }
        if (rawClass == Instant.class || rawClass == OffsetDateTime.class || rawClass == LocalDateTime.class) {
            Map<String, Object> descriptor = scalarDescriptor("string");
            descriptor.put("format", "date-time");
            return descriptor;
        }
        if (rawClass == LocalDate.class) {
            Map<String, Object> descriptor = scalarDescriptor("string");
            descriptor.put("format", "date");
            return descriptor;
        }
        if (isContractType(rawClass)) {
            pending.add(rawClass);
            Map<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put("kind", "ref");
            descriptor.put("name", contractName(rawClass));
            return descriptor;
        }
        if (rawClass == Object.class) {
            Map<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put("kind", "unknown");
            return descriptor;
        }
        return unknownDescriptor(rawClass);
    }

    private Map<String, Object> parameterizedDescriptor(ParameterizedType type, Queue<Class<?>> pending) {
        Type rawType = type.getRawType();
        if (!(rawType instanceof Class<?> rawClass)) {
            return unknownDescriptor(type);
        }
        if (Collection.class.isAssignableFrom(rawClass)) {
            Type itemType = firstTypeArgument(type, Object.class);
            return arrayDescriptor(typeDescriptor(itemType, pending));
        }
        if (Map.class.isAssignableFrom(rawClass)) {
            Type valueType = type.getActualTypeArguments().length > 1
                ? type.getActualTypeArguments()[1]
                : Object.class;
            Map<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put("kind", "map");
            descriptor.put("valueType", typeDescriptor(valueType, pending));
            return descriptor;
        }
        return classDescriptor(rawClass, pending);
    }

    private Type firstTypeArgument(ParameterizedType type, Type fallback) {
        Type[] arguments = type.getActualTypeArguments();
        return arguments.length == 0 ? fallback : arguments[0];
    }

    private Map<String, Object> scalarDescriptor(String kind) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("kind", kind);
        return descriptor;
    }

    private Map<String, Object> arrayDescriptor(Map<String, Object> itemType) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("kind", "array");
        descriptor.put("itemType", itemType);
        return descriptor;
    }

    private Map<String, Object> unknownDescriptor(Type type) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("kind", "unknown");
        descriptor.put("javaType", type.getTypeName());
        return descriptor;
    }

    private boolean isContractType(Class<?> type) {
        return type.getName().startsWith(MODEL_PACKAGE + ".");
    }

    private String contractName(Class<?> type) {
        return type.getName()
            .substring((MODEL_PACKAGE + ".").length())
            .replace('$', '.');
    }

    private String jsonPropertyName(RecordComponent component) {
        JsonProperty annotation = component.getAnnotation(JsonProperty.class);
        if (annotation == null) {
            Method accessor = component.getAccessor();
            annotation = accessor == null ? null : accessor.getAnnotation(JsonProperty.class);
        }
        if (annotation != null && !annotation.value().isBlank()) {
            return annotation.value();
        }
        return component.getName();
    }

    private Object jsonValueOf(Object value) {
        Object converted = objectMapper.convertValue(value, Object.class);
        if (converted instanceof Map<?, ?> || converted instanceof List<?> || converted != null && converted.getClass().isArray()) {
            return converted.getClass().isArray() ? arrayToList(converted) : converted;
        }
        return converted;
    }

    private List<Object> arrayToList(Object array) {
        int length = Array.getLength(array);
        List<Object> values = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            values.add(Array.get(array, index));
        }
        return values;
    }
}
