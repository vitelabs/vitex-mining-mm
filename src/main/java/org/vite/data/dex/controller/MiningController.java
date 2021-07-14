package org.vite.data.dex.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class MiningController {

    @RequestMapping("/mining/reward")
    public String getMiningReward(){
        return "hello vitex";
    }
}
