package org.vite.data.dex;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.vite.dex.mm.DexApplication;
import org.vite.dex.mm.orderbook.TradeRecover;

import java.io.IOException;

@SpringBootTest(classes = DexApplication.class)
class DexApplicationTests {

    @Autowired
    TradeRecover tradeRecover;

    @Test
    void contextLoads() {
    }

    @Test
    public void testOrderBooks() throws IOException {
        tradeRecover.prepareOrderBooks();
    }
}
