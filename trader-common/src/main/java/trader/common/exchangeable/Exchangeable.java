package trader.common.exchangeable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import trader.common.exchangeable.Exchange.MarketType;

public abstract class Exchangeable implements Comparable<Exchangeable> {

    static class ExchangeableTradingMarketInfo implements TradingMarketInfo{
        private LocalDate tradingDay;
        private MarketType market;
        private LocalDateTime[] marketTimes;

        @Override
        public LocalDate getTradingDay() {
            return tradingDay;
        }

        @Override
        public MarketType getMarket() {
            return market;
        }

        @Override
        public LocalDateTime[] getMarketTimes() {
            return marketTimes;
        }

        @Override
        public LocalDateTime getMarketOpenTime(){
            return marketTimes[0];
        }

        @Override
        public LocalDateTime getMarketCloseTime(){
            return marketTimes[marketTimes.length-1];
        }

    }

    protected Exchange exchange;
    protected String id;
    protected ExchangeableType type;
    protected String uniqueId;
    protected String name;
    protected int uniqueIntId;

    protected Exchangeable(Exchange exchange, String id){
        this(exchange, id, id);
    }

    protected Exchangeable(Exchange exchange, String id, String name){
        this.exchange = exchange;
        this.id = id;
        this.name = name;
        if ( name==null ) {
            this.name = id;
        }
        this.type = detectType();
        uniqueId = exchange.name()+"."+id;
        uniqueIntId = genUniqueIntId(uniqueId);
    }

    /**
     * 名称
     */
    public String name(){
        return name;
    }

    /**
     * ID,对于期货.是品种+合约的全名
     */
    public String id(){
        return id;
    }

    public String uniqueId(){
        return uniqueId;
    }

    /**
     * Integer ID
     */
    public int uniqueIntId(){
        return uniqueIntId;
    }

    /**
     * 期货品种名称, 或者股票ID
     */
    public String commodity(){
        return id;
    }

    public Exchange exchange(){
        return exchange;
    }

    public ExchangeableType getType(){
        return type;
    }

    public String toPrintableString(){
        if (name==null) {
            return uniqueId;
        } else {
            return uniqueId+" "+name;
        }
    }

    /**
     * 探测交易日相关信息
     */
    public TradingMarketInfo detectTradingMarketInfo(LocalDateTime marketTime){
        ExchangeableTradingMarketInfo result = new ExchangeableTradingMarketInfo();
        LocalDate day = marketTime.toLocalDate();

        result.market = MarketType.Day;
        result.marketTimes = exchange.getMarketTimes(MarketType.Day, commodity(), day);
        if ( MarketDayUtil.isMarketDay(exchange, day)){
            //日盘
            result.tradingDay = day;
            LocalDateTime dayOpenTime = result.marketTimes[0];
            LocalDateTime dayCloseTime = result.marketTimes[result.marketTimes.length-1];
            LocalDateTime dayOpenTime_M1 = dayOpenTime.minusHours(1);
            LocalDateTime dayCloseTime_P1 = dayCloseTime.plusHours(1);
            //日盘.开盘前一小时 -- 收盘后一小时
            if ( marketTime.isAfter(dayOpenTime_M1) && marketTime.isBefore(dayCloseTime_P1)){
                return result;
            }
        }
        //无夜盘, 不需要处理
        if( !exchange.hasMarket(MarketType.Night)){
            return null;
        }
        LocalDateTime dayCloseTime = result.marketTimes[result.marketTimes.length-1];
        LocalDate tradingDay = null;
        if ( marketTime.getHour()>=0 && marketTime.getHour()<=3 ){
            //检查是否当日的夜盘 0:00-3:00
            tradingDay = MarketDayUtil.nextMarketDay(exchange, day.minusDays(1) );
        }else if ( marketTime.getHour()>= dayCloseTime.getHour()){
            //检查是否是下一个交易日的夜盘
            tradingDay = MarketDayUtil.nextMarketDay(exchange, day);
        }else{
            return null;
        }
        LocalDateTime[] nightMarketTimes = exchange.getMarketTimes(MarketType.Night, commodity(), tradingDay);
        LocalDateTime nightOpenTime = nightMarketTimes[0];
        LocalDateTime nightCloseTime = nightMarketTimes[1];
        LocalDateTime nightOpenTime_M1 = nightOpenTime.minusHours(1);
        LocalDateTime nightCloseTime_P1 = nightCloseTime.plusHours(1);
        if ( marketTime.isAfter(nightOpenTime_M1) && marketTime.isBefore(nightCloseTime_P1) ){
            result.marketTimes = nightMarketTimes;
            result.market = MarketType.Night;
            result.tradingDay = tradingDay;
            return result;
        }
        return null;
    }

    /**
     * 探测交易日
     */
    public LocalDate detectTradingDay(LocalDateTime marketTime){
        TradingMarketInfo marketInfo = detectTradingMarketInfo(marketTime);
        if ( marketInfo!=null){
            return marketInfo.getTradingDay();
        }
        return null;
    }

    public LocalDateTime[] getOpenCloseTime(LocalDate tradingDay){
        return exchange.getOpenCloseTime(MarketType.Day, commodity(), tradingDay);
    }

    public int getTradingMilliSeconds(LocalDateTime marketTime){
        return getTradingMilliSeconds(marketTime.toLocalDate(), marketTime.toLocalTime());
    }

    public int getTradingMilliSeconds(LocalDate tradingDay, LocalTime marketTime){
        return exchange.getTradingMilliSeconds(MarketType.Day, commodity(), tradingDay, marketTime);
    }

    public MarketTimeStage getTimeStage(LocalDate tradingDay, LocalDateTime time){
        return getTimeFrame(MarketType.Day, tradingDay, time);
    }

    public MarketTimeStage getTimeFrame(LocalDateTime ldt){
        return getTimeFrame(MarketType.Day, ldt.toLocalDate(), ldt);
    }

    public LocalDateTime[] getMarketTimes(String instrumentId, LocalDate tradingDay){
        return exchange.getMarketTimes(MarketType.Day, commodity(), tradingDay);
    }


    public LocalDateTime[] getMarketTimes(MarketType marketType, String instrumentId, LocalDate tradingDay){
        return exchange.getMarketTimes(marketType, commodity(), tradingDay);
    }

    public LocalDateTime[] getOpenCloseTime(MarketType marketType, LocalDate tradingDay){
        return exchange.getOpenCloseTime(marketType, commodity(), tradingDay);
    }

    public int getTradingMilliSeconds(MarketType marketType, LocalDate tradingDay, LocalTime marketTime){
        return exchange.getTradingMilliSeconds(marketType, commodity(), tradingDay, marketTime);
    }

    public MarketTimeStage getTimeFrame(MarketType marketType, LocalDate tradingDay, LocalDateTime time){
        LocalDateTime[] marketTimes = getMarketTimes(marketType, commodity(), tradingDay);
        if ( marketTimes==null ){
            return null;
        }
        LocalDateTime marketOpenTime = marketTimes[0];
        LocalDateTime aggregateAuctionTime = marketOpenTime.minusMinutes(5);
        LocalDateTime marketCloseTime = marketTimes[marketTimes.length-1];
        if ( time.isBefore(aggregateAuctionTime) ){
            return MarketTimeStage.BeforeMarketOpen;
        }
        if ( time.isAfter(aggregateAuctionTime) && time.isBefore(marketOpenTime)){
            return MarketTimeStage.AggregateAuction;
        }

        for(int i=0; i<marketTimes.length; i+=2){
            LocalDateTime tradeBeginTime = marketTimes[i];
            LocalDateTime tradeEndTime = marketTimes[i+1];

            if ( time.compareTo(tradeBeginTime)>=0 && time.compareTo(tradeEndTime)<=0 ){
                return MarketTimeStage.MarketOpen;
            }

            boolean lastFragment = i>=(marketTimes.length-2);
            if ( !lastFragment && time.isAfter(tradeEndTime)){
                LocalDateTime nextBeginTime = marketTimes[i+2];
                if ( time.isBefore(nextBeginTime)){
                    return MarketTimeStage.MarketBreak;
                }
            }
        }
        if ( time.isAfter(marketCloseTime) ){
            return MarketTimeStage.MarketClose;
        }
        throw new RuntimeException("Should not run here");
    }

    @Override
    public String toString(){
        return uniqueId;
    }

    @Override
    public boolean equals(Object o){
        if ( o==null || !(o instanceof Exchangeable) ){
            return false;
        }
        Exchangeable s = (Exchangeable)o;
        return uniqueIntId == s.uniqueIntId;
    }

    @Override
    public int hashCode(){
        return uniqueId.hashCode();
    }

    protected ExchangeableType detectType(){
        ExchangeableType type = ExchangeableType.OTHER;
        if ( exchange==Exchange.SSE ){
            if ( id.startsWith("00") ){
                type = ExchangeableType.INDEX;
            }else if ( id.startsWith("01") ){
                type = ExchangeableType.BOND;
            }else if ( id.startsWith("11") ){
                type = ExchangeableType.CONVERTABLE_BOND;
            }else if ( id.startsWith("12") ){
                type=ExchangeableType.BOND;
            }else if ( id.startsWith("20") ){
                type=ExchangeableType.BOND_REPURCHARSE;
            }else if ( id.startsWith("50") || id.startsWith("51") ){
                type=ExchangeableType.FUND;
            }else if ( id.startsWith("60") ){
                type=ExchangeableType.STOCK;
            }else if ( id.startsWith("90") ){//B股
                type=ExchangeableType.STOCK;
            }
        }else if ( exchange==Exchange.SZSE ){
            if ( id.startsWith("00")){
                type = ExchangeableType.STOCK;
            }else if ( id.startsWith("10")||id.startsWith("11") ){
                type = ExchangeableType.BOND;
            }else if ( id.startsWith("12") ){
                type = ExchangeableType.CONVERTABLE_BOND;
            }else if ( id.startsWith("13") ){
                type = ExchangeableType.BOND_REPURCHARSE;
            }else if ( id.startsWith("15") || id.startsWith("16") ){
                type = ExchangeableType.FUND;
            }else if ( id.startsWith("20")){ //B股
                type = ExchangeableType.STOCK;
            }else if ( id.startsWith("30")){ //创业板
                type = ExchangeableType.STOCK;
            }else if ( id.startsWith("39") ){
                type = ExchangeableType.INDEX;
            }
        }else if (exchange ==Exchange.HKEX) {

            if (id.startsWith("99")) { //沪股通990001, 深股通990002
                type = ExchangeableType.INDEX;
            }
        } else if ( exchange==Exchange.CFFEX
                || exchange==Exchange.DCE
                || exchange==Exchange.SHFE)
        { //期货
            type = ExchangeableType.FUTURE;
        }
        return type;
    }

    public static Exchangeable create(Exchange exchange, String instrumentId){
        return create(exchange, instrumentId, null);
    }

    public static Exchangeable create(Exchange exchange, String instrumentId, String name){
        if ( exchange==Exchange.SSE || exchange==Exchange.SZSE || exchange==Exchange.DCE || exchange==Exchange.CZCE ){
            return new Security(exchange, instrumentId, name);
        }else if ( exchange==Exchange.CFFEX|| exchange==Exchange.DCE || exchange==Exchange.CZCE || exchange==Exchange.SHFE ){
            return new Future(exchange, instrumentId, name);
        }else if ( exchange==null ){
            return Future.fromString(instrumentId);
        }
        throw new RuntimeException("Unknown exchange: "+exchange);
    }


    private static Map<String, Exchangeable> cachedExchangeables = new HashMap<>();
    private static Lock cachedExchangeableLock = new ReentrantLock();

    /**
     * Load exchangeable from cache
     */
    public static Exchangeable fromString(String str){
        cachedExchangeableLock.lock();
        try{
            Exchangeable result = cachedExchangeables.get(str);
            if ( result!=null ) {
                return result;
            }

            int idx = str.indexOf('.');
            if ( idx<0 ){
                result = Future.fromInstrument(str);
            }else{
                String exchangeName = str.substring(0,idx);
                String id = str.substring(idx+1);
                Exchange exchange = Exchange.getInstance(exchangeName);
                if ( exchange==null ){
                    String tmp = id;
                    id = exchangeName;
                    exchangeName = tmp;
                    exchange = Exchange.getInstance(exchangeName);
                }

                if ( exchange==Exchange.SSE || exchange==Exchange.SZSE ){
                    result = new Security(exchange, id);
                }else if ( exchange==Exchange.CFFEX || exchange==Exchange.SHFE || exchange==Exchange.DCE){
                    result = new Future(exchange, str.substring(idx+1));
                } else {
                    throw new RuntimeException("Unknown exchangeable string: "+str);
                }
            }
            cachedExchangeables.put(str, result);
            return result;
        }finally{
            cachedExchangeableLock.unlock();
        }
    }
    /**
     * Load exchangeable from cache
     */
    public static Exchangeable fromString(String exchangeStr, String instrumentStr){
        return fromString(exchangeStr, instrumentStr, null);
    }

    /**
     * Load exchangeable from cache
     */
    public static Exchangeable fromString(String exchangeStr, String instrumentStr, String instrumentName){
        cachedExchangeableLock.lock();
        try{
            String uniqueStr = null;
            if ( exchangeStr!=null ) {
                uniqueStr = exchangeStr+"."+instrumentStr;
            } else {
                uniqueStr = instrumentStr;
            }

            Exchangeable result = cachedExchangeables.get(uniqueStr);
            if ( result!=null ) {
                return result;
            }

            if ( exchangeStr==null ){
                result = Future.fromString(uniqueStr);
            }else{
                Exchange exchange = Exchange.getInstance(exchangeStr);

                if ( exchange==Exchange.SSE || exchange==Exchange.SZSE ){
                    result = new Security(exchange, instrumentStr, instrumentName);
                }else if ( exchange==Exchange.CFFEX || exchange==Exchange.SHFE || exchange==Exchange.DCE){
                    result = new Future(exchange, instrumentStr, instrumentName);
                }else{
                    throw new RuntimeException("Unknown exchangeable string: "+uniqueStr);
                }
            }
            cachedExchangeables.put(uniqueStr, result);
            return result;
        }finally{
            cachedExchangeableLock.unlock();
        }
    }

    /**
     * Update cache with pre-created entries
     */
    public static void populateCache(Collection<Exchangeable> instruments)
    {
        if ( instruments==null ){
            return;
        }
        cachedExchangeableLock.lock();
        try{
            for(Exchangeable e:instruments){
                cachedExchangeables.put(e.toString(), e);
            }
        }finally{
            cachedExchangeableLock.unlock();
        }
    }

    /**
     * 结算周期
     * <BR>0 = T+0
     * <BR>1 = T+1
     */
    public int getSettlementPeriod(){
        if ( exchange==Exchange.SSE || exchange==Exchange.SZSE ){
            switch( getType()) {
            case BOND:
            case OPTION:
                return 0;
            default:
                return 1;
            }
        }else{
            return 0;
        }
    }

    @Override
    public int compareTo(Exchangeable o)
    {
        return uniqueId.compareTo(o.uniqueId);
    }

    private static AtomicInteger nextExchangeableId = new AtomicInteger();
    private static Map<String, Integer> exchangeableIds = new HashMap<>();
    private static int genUniqueIntId(String uniqueId){
        Integer id = null;
        synchronized(exchangeableIds){
            id = exchangeableIds.get(uniqueId);
            if ( id==null ){
                id = nextExchangeableId.getAndIncrement();
                exchangeableIds.put(uniqueId, id);
            }
        }
        return id;
    }

    /**
     * 港股通
     */
    public static Exchangeable HKEX_GGT = new Security(Exchange.HKEX, "990001", "港股通");
    /**
     * 深股通
     */
    public static Exchangeable HKEX_SGT = new Security(Exchange.HKEX, "990002", "深股通");
}