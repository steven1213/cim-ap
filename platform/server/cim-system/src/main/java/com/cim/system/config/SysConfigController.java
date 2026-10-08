package com.cim.system.config;

import com.cim.spring.support.service.CrudService;
import com.cim.spring.support.web.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 参数配置接口（CRUD 继承自 {@link BaseController}，鉴权在 {@link SysConfigService}）。 */
@RestController
@RequestMapping("/sys/configs")
public class SysConfigController extends BaseController<SysConfig, String> {

    private final SysConfigService configService;

    public SysConfigController(SysConfigService configService) {
        this.configService = configService;
    }

    @Override
    protected CrudService<SysConfig, String> service() {
        return configService;
    }
}
