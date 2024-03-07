package org.vite.dex.mm.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.vite.dex.mm.http.ResultBean;
import org.vite.dex.mm.http.ResultCode;
import org.vite.dex.mm.reward.RewardEngine;

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
    @RequestMapping(value = "/cycle/run", method = RequestMethod.GET)
    public ResultBean invokeDailyRunByCycle(@RequestParam(name = "cycleKey") int cycleKey) {
        try {
            engine.runCycle(cycleKey);
            return new ResultBean<>(ResultCode.SUCCESS, "ok", null);
        } catch (Exception e) {
            log.error("/cycle/run, e:{}", e);
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
