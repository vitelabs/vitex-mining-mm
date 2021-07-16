package org.vite.data.dex.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartup implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        System.out.println("ApplicationReadyEvent: application is up");
        try {
            // 程序启动之后，便开始从vitex链拉取订单事件。（上一个周期的所有事件）

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
