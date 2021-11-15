package org.vite.dex.mm.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.vite.dex.mm.model.pojo.http.ResultBean;
import org.vite.dex.mm.model.pojo.http.ResultCode;
import org.vite.dex.mm.scheduler.RewardEngine;

import javax.annotation.Resource;

@RestController
@RequestMapping("/v1")
@Slf4j
public class MiningController {
    @Resource
    private RewardEngine engine;

    @ResponseBody
    @RequestMapping(value = "/daily/run", method = RequestMethod.GET)
    public ResultBean invokeDailyRun() {
        try {
            engine.runDaily();
            return new ResultBean<>(ResultCode.SUCCESS, "ok", null);
        } catch (Exception e) {
            log.error("/daily/run, e:{}", e);
            return new ResultBean<>(ResultCode.ERROR, e.getMessage(), null);
        }
    }

    @ResponseBody
    @RequestMapping(value = "/halfHour/run", method = RequestMethod.GET)
    public ResultBean invokeHalfHourRun() {
        try {
            engine.runHalfHour();
            return new ResultBean<>(ResultCode.SUCCESS, "ok", null);
        } catch (Exception e) {
            log.error("/halfHour/run, e:{}", e);
            return new ResultBean<>(ResultCode.ERROR, e.getMessage(), null);
        }
    }
}
