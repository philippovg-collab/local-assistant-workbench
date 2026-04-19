package com.example.demo.support;

import java.util.Iterator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;

public final class TestObjectProviders {

    private TestObjectProviders() {
    }

    public static <T> ObjectProvider<T> empty() {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return null;
            }

            @Override
            public T getObject(Object... args) {
                return null;
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }

            @Override
            public Iterator<T> iterator() {
                return List.<T>of().iterator();
            }
        };
    }
}
