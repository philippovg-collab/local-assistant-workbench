package com.example.demo.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

class OperatorAuditCoverageTest {

    private static final Set<String> AUDITED_ENDPOINTS = Set.of(
        "POST /api/auth/login",
        "POST /api/auth/logout",
        "POST /api/chat",
        "POST /api/chat-runs",
        "POST /api/chat-runs/{id}/cancel",
        "POST /api/materials",
        "POST /api/materials/upload",
        "POST /api/materials/{id}/versions",
        "PUT /api/materials/{id}",
        "DELETE /api/materials/{id}",
        "POST /api/materials/{id}/reindex",
        "POST /api/materials/rechunk-active",
        "POST /api/materials/rechunk-active/batch",
        "POST /api/instructions",
        "PUT /api/instructions/{id}",
        "DELETE /api/instructions/{id}",
        "POST /api/instructions/{id}/restore/{revision}",
        "POST /api/knowledge-presets",
        "PUT /api/knowledge-presets/{id}",
        "DELETE /api/knowledge-presets/{id}",
        "POST /api/knowledge-presets/{id}/restore/{revision}",
        "POST /api/knowledge-facets",
        "PUT /api/knowledge-facets/{id}",
        "DELETE /api/knowledge-facets/{id}",
        "POST /api/knowledge-facets/{id}/restore/{revision}",
        "POST /api/reference/workspaces",
        "PUT /api/reference/workspaces/{key}",
        "POST /api/reference/projects",
        "PUT /api/reference/projects/{key}",
        "POST /api/rag-projects",
        "PUT /api/rag-projects/{key}",
        "POST /api/conversations",
        "PATCH /api/conversations/{id}",
        "POST /api/memory-entries",
        "PATCH /api/memory-entries/{id}",
        "DELETE /api/memory-entries/{id}",
        "POST /api/memory-entries/{id}/approve",
        "POST /api/memory-entries/{id}/reject",
        "POST /api/memory-entries/{id}/pin",
        "POST /api/memory-entries/{id}/unpin",
        "POST /api/llm-providers",
        "PUT /api/llm-providers/{id}",
        "DELETE /api/llm-providers/{id}",
        "POST /api/llm-providers/{id}/probe",
        "POST /api/llm-providers/{id}/activate",
        "POST /api/llm-providers/fallback/activate"
    );

    private static final Map<String, String> ALLOWLIST = Map.of(
        "POST /api/search", "read-like query; covered by access log only",
        "POST /api/client-events", "frontend telemetry endpoint; intentionally not durable operator audit"
    );

    @Test
    void everyUnsafeControllerMappingIsAuditedOrExplicitlyAllowlisted() throws Exception {
        List<String> uncovered = unsafeControllerEndpoints().stream()
            .filter(endpoint -> !AUDITED_ENDPOINTS.contains(endpoint) && !ALLOWLIST.containsKey(endpoint))
            .sorted()
            .toList();

        assertTrue(
            uncovered.isEmpty(),
            "Unsafe controller mappings must have operator audit coverage or explicit allowlist reason: " + uncovered
        );
    }

    private List<String> unsafeControllerEndpoints() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        return scanner.findCandidateComponents("com.example.demo.controller").stream()
            .map(beanDefinition -> loadClass(beanDefinition.getBeanClassName(), classLoader))
            .flatMap(controllerClass -> unsafeControllerEndpoints(controllerClass).stream())
            .distinct()
            .sorted(Comparator.naturalOrder())
            .toList();
    }

    private Class<?> loadClass(String className, ClassLoader classLoader) {
        try {
            return ClassUtils.forName(className, classLoader);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to load controller class: " + className, exception);
        }
    }

    private List<String> unsafeControllerEndpoints(Class<?> controllerClass) {
        String[] basePaths = requestMappingPaths(AnnotatedElementUtils.findMergedAnnotation(
            controllerClass,
            RequestMapping.class
        ));
        java.util.ArrayList<String> endpoints = new java.util.ArrayList<>();
        for (java.lang.reflect.Method method : controllerClass.getDeclaredMethods()) {
            endpoints.addAll(mappingEndpoints(basePaths, "POST", postMappingPaths(method)));
            endpoints.addAll(mappingEndpoints(basePaths, "PUT", putMappingPaths(method)));
            endpoints.addAll(mappingEndpoints(basePaths, "PATCH", patchMappingPaths(method)));
            endpoints.addAll(mappingEndpoints(basePaths, "DELETE", deleteMappingPaths(method)));
            endpoints.addAll(requestMappingEndpoints(basePaths, method));
        }
        return endpoints;
    }

    private List<String> requestMappingEndpoints(String[] basePaths, java.lang.reflect.Method method) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null || mapping.method().length == 0) {
            return List.of();
        }
        String[] methodPaths = requestMappingPaths(mapping);
        java.util.ArrayList<String> endpoints = new java.util.ArrayList<>();
        for (RequestMethod requestMethod : mapping.method()) {
            if (isUnsafeMethod(requestMethod)) {
                endpoints.addAll(mappingEndpoints(basePaths, requestMethod.name(), methodPaths));
            }
        }
        return endpoints;
    }

    private boolean isUnsafeMethod(RequestMethod requestMethod) {
        return requestMethod == RequestMethod.POST
            || requestMethod == RequestMethod.PUT
            || requestMethod == RequestMethod.PATCH
            || requestMethod == RequestMethod.DELETE;
    }

    private String[] postMappingPaths(java.lang.reflect.Method method) {
        PostMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class);
        return mapping == null ? new String[0] : paths(mapping.value(), mapping.path());
    }

    private String[] putMappingPaths(java.lang.reflect.Method method) {
        PutMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, PutMapping.class);
        return mapping == null ? new String[0] : paths(mapping.value(), mapping.path());
    }

    private String[] patchMappingPaths(java.lang.reflect.Method method) {
        PatchMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, PatchMapping.class);
        return mapping == null ? new String[0] : paths(mapping.value(), mapping.path());
    }

    private String[] deleteMappingPaths(java.lang.reflect.Method method) {
        DeleteMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, DeleteMapping.class);
        return mapping == null ? new String[0] : paths(mapping.value(), mapping.path());
    }

    private String[] requestMappingPaths(RequestMapping mapping) {
        return mapping == null ? new String[] { "" } : paths(mapping.value(), mapping.path());
    }

    private String[] paths(String[] values, String[] paths) {
        if (paths.length > 0) {
            return paths;
        }
        if (values.length > 0) {
            return values;
        }
        return new String[] { "" };
    }

    private List<String> mappingEndpoints(String[] basePaths, String method, String[] methodPaths) {
        if (methodPaths.length == 0) {
            return List.of();
        }
        java.util.ArrayList<String> endpoints = new java.util.ArrayList<>();
        for (String basePath : basePaths) {
            for (String methodPath : methodPaths) {
                endpoints.add(method + " " + joinPaths(basePath, methodPath));
            }
        }
        return endpoints;
    }

    private String joinPaths(String basePath, String methodPath) {
        if (!StringUtils.hasText(basePath)) {
            return normalizePath(methodPath);
        }
        if (!StringUtils.hasText(methodPath)) {
            return normalizePath(basePath);
        }
        return normalizePath(basePath.replaceAll("/+$", "") + "/" + methodPath.replaceAll("^/+", ""));
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
