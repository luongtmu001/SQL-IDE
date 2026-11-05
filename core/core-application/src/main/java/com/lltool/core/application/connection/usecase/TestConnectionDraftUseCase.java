package com.lltool.core.application.connection.usecase;

import com.lltool.core.application.connection.command.TestConnectionDraftCommand;
import com.lltool.core.common.result.Result;

public interface TestConnectionDraftUseCase {
    Result<Boolean> execute(TestConnectionDraftCommand command);
}

