package com.lltool.core.api.request;

import java.io.Serializable;

public record CreateProjectRequest(String name, String path) implements Serializable {}

