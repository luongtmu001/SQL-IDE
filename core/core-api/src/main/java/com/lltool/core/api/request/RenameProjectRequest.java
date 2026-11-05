package com.lltool.core.api.request;

import java.io.Serializable;

public record RenameProjectRequest(String projectId, String newName) implements Serializable {}

