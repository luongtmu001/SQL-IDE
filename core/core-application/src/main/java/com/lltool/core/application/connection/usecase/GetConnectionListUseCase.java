package com.lltool.core.application.connection.usecase;

import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.connection.query.GetConnectionListQuery;
import com.lltool.core.common.result.Result;

import java.util.List;

public interface GetConnectionListUseCase {
    Result<List<ConnectionProfileDto>> execute(GetConnectionListQuery query);
}

