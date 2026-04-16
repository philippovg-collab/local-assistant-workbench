package com.example.demo.infrastructure.material;

import java.nio.file.Path;

public interface OcrClient {

    String extract(Path imagePath, int pageNumber);
}
