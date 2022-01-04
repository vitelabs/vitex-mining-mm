![](https://gimg2.baidu.com/image_search/src=http%3A%2F%2Fwww.cryptoninjas.net%2Fwp-content%2Fuploads%2Fvite-network-crypto-ninjas.png&refer=http%3A%2F%2Fwww.cryptoninjas.net&app=2002&size=f9999,10000&q=a80&n=0&g=0n&fmt=jpeg?sec=1629619941&t=35efe21ace7cb9b2669e394c42773753)

## ViteX order-mining mm
### 1.1 Description

- The order-mining calculate engine is based on SpringBoot Scheduler
- The running speed is quite fast due to pure memory computation,does not rely on database at all.
- The algorithm gets the same result no matter how many times it runs

### 1.2 Algorithm architecture

1. Firstly, get the curruent order book of the specified trade pair and all the emitted events from the trade constract at a time range.
2. Secondly, Restore the order book to the state at a previous point by redoing the sequencial event.
3. Thirdly, begin with the old order book and do forward with the events so as to calculate reward of each order.
4. Finally, accumulate the reward of diffent market of all the orders for a user.


### 1.3 **Somethings to note**
- The previous cycle starts at 12:00 p.m. yesterday and ends at 12 p.m. today. Therefore, the mining results of the previous cycle will be calculated on 13:00 p.m today.