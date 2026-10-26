package com.interviewmind.modules.health;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    @GetMapping
    public Result<Map<String,Object>> handleHealthCheck() {
        return Result.success(Map.of(
                "status", "UP",
                "service", "interviewmind",
                "time", LocalDateTime.now().toString()
        ));
    }
    @GetMapping("/business-error")
    public Result<Void> businessError() {
        throw new BusinessException(ErrorCode.NOT_FOUND, "这是一条模拟的业务异常");
    }

    /** 系统异常：验证 Exception 兜底处理器 */
    @GetMapping("/system-error")
    public Result<Void> systemError() {
        throw new RuntimeException("这是一条模拟的系统异常");
    }

    /** 非法参数：验证 IllegalArgumentException 处理器 */
    @GetMapping("/illegal-argument")
    public Result<Void> illegalArgument() {
        throw new IllegalArgumentException("参数 id 不能为负数");
    }
}
