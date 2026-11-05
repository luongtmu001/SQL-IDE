package com.lltool.core.application.connection.usecase;

import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.connection.query.GetConnectionDetailQuery;
import com.lltool.core.common.result.Result;

public interface GetConnectionDetailUseCase {
    Result<ConnectionProfileDto> execute(GetConnectionDetailQuery query);
}

