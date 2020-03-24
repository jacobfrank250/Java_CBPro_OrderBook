package liveorderbook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;

import org.erc.coinbase.pro.exceptions.CoinbaseException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;

public class OrderBookTest {
    static TestBook tb = new TestBook(URI.create("wss://ws-feed.pro.coinbase.com"));

    @BeforeAll
    public static void setup() {
        TestResultManager.clearTestResultsDir();
        tb.connect();
    }

    @BeforeEach
    public void beforeEachTest() throws InterruptedException {
        Thread.sleep(5000);

    }

    @AfterEach
    public void afterEachTest() throws IOException {

        tb.bidsLog = new LinkedHashMap<BigDecimal, HashMap<BigDecimal, List<Order>>>() {
            protected boolean removeEldestEntry(Map.Entry<BigDecimal, HashMap<BigDecimal, List<Order>>> eldest) {
                return size() > tb.MAX;
            }
        };

        tb.bidsLog = new LinkedHashMap<BigDecimal, HashMap<BigDecimal, List<Order>>>() {
            protected boolean removeEldestEntry(Map.Entry<BigDecimal, HashMap<BigDecimal, List<Order>>> eldest) {
                return size() > tb.MAX;
            }
        };

        System.out.println("=====================");
    }

    @AfterAll
    public static void shutdown() {
        System.out.println("Closing socket");

        tb.close();
    }

    // @Test
    @RepeatedTest(20)
    public void isOrderBookCorrect(RepetitionInfo repetitionInfo) throws InterruptedException, CoinbaseException {
        String testIteration = "Test #" + repetitionInfo.getCurrentRepetition() + " of "
                + repetitionInfo.getTotalRepetitions();

        System.out.println(testIteration + "\n");

        TreeMap<BigDecimal, List<Order>> restAsks = new TreeMap<BigDecimal, List<Order>>();
        TreeMap<BigDecimal, List<Order>> restBids = new TreeMap<BigDecimal, List<Order>>();

        BigDecimal restSequence = getRestOrders(restBids, restAsks);
        String snapshotTime = OrderBookRestRequest.getTime();

        // Debug message to show state of sequence logs 
        // if((!tb.asksLog.containsKey(restSequence) && !tb.bidsLog.containsKey(restSequence)))
        if(tb.asksLog.get(restSequence) == null || tb.bidsLog.get(restSequence) == null)
        {
            BigDecimal sequenceDelta = restSequence.subtract(tb.sequence);     
            System.out.println("Last logged socket sequence " + tb.sequence + " (\u0394" + sequenceDelta + ")");
        }


        // while (!tb.asksLog.containsKey(restSequence) && !tb.bidsLog.containsKey(restSequence)) {
        while (tb.asksLog.get(restSequence) == null || tb.bidsLog.get(restSequence) == null) {

            BigDecimal seqDiff = restSequence.subtract(tb.sequence);
            if(seqDiff.compareTo(BigDecimal.ZERO)<0)
            {
                /**  
                The order logs do not contain the sequence returned by the rest request.
                This is not because we havent proccessed the sequence message,
                But because its so far back that our size-limited linkedhashmap that stores the order snap shots has deleted it already
                
                In this case we should load another rest request to test against
                */
                System.out.println("Making another rest request to test against.");
                restAsks.clear();;
                restBids.clear();
                // We must wait before making another rest request so as not to exceed rate limit
                Thread.sleep(500);

                restSequence = getRestOrders(restBids, restAsks);
                snapshotTime = OrderBookRestRequest.getTime();
            }
            Thread.sleep(1);
        }
      
        System.out.println("\nTesting at sequence: " + restSequence + "\n");

        TreeMap<BigDecimal, List<Order>> asksAtRestSequence = new TreeMap<BigDecimal, List<Order>>();
        asksAtRestSequence.putAll(tb.asksLog.get(restSequence));

        TreeMap<BigDecimal, List<Order>> bidsAtRestSequence = new TreeMap<BigDecimal, List<Order>>();
        bidsAtRestSequence.putAll(tb.bidsLog.get(restSequence));

        
        // Get the prices in the rest and socket order books
        Set<BigDecimal> socketAskPrices = asksAtRestSequence.keySet();
        Set<BigDecimal> restAskPrices = restAsks.keySet();
        Set<BigDecimal> socketBidPrices = bidsAtRestSequence.keySet();
        Set<BigDecimal> restBidPrices = restBids.keySet();

        // Check all the prices in the order book are the same
        assertEquals(restAskPrices, socketAskPrices, testIteration);
        assertEquals(restBidPrices, socketBidPrices, testIteration);
        
        // Check that every order at each price level is the same
        assertOrderListsAtEachPriceLevel(testIteration, restSequence, "ask", asksAtRestSequence, restAsks);
        assertOrderListsAtEachPriceLevel(testIteration, restSequence, "bid", bidsAtRestSequence, restBids);
        
        System.out.println("\n" + snapshotTime);

        // Write to csv file
        try {
            String dir = TestResultManager.createTestResultDir("test" + repetitionInfo.getCurrentRepetition());
            TestResultManager.writeTestResultToFile(dir, "ask", restSequence, asksAtRestSequence, restAsks,
                    snapshotTime);
            TestResultManager.writeTestResultToFile(dir, "bid", restSequence, bidsAtRestSequence, restBids,
                    snapshotTime);
        
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    void assertOrderListsAtEachPriceLevel(String testIteration, BigDecimal seq, String side, TreeMap<BigDecimal, List<Order>> socketTreeTested, TreeMap<BigDecimal, List<Order>> restTreeTested)
    {
        int numDisparities = 0;
        NavigableSet<BigDecimal> prices = side.equals("bid")? restTreeTested.descendingKeySet() : restTreeTested.navigableKeySet();
        for(BigDecimal price : prices)
        {
            List<Order> restOrders = restTreeTested.get(price);
            BigDecimal restSize = BigDecimal.ZERO;
            int restNumOrders = restOrders.size();
            for(Order order:restOrders){
                restSize = restSize.add(order.size);
            }

            List<Order> socketOrders = socketTreeTested.get(price);
            BigDecimal socketSize = BigDecimal.ZERO;
            int socketNumOrders = socketOrders.size();
            for(Order order:socketOrders){
                socketSize = socketSize.add(order.size);
            }
            
            String numOrdersFailMessage =  testIteration + "\n(" + side + ") num orders are not equal at price " + price
            + "\n-->rest: " + restNumOrders + ", socket: " + socketNumOrders;
            String sizeFailMessage =  testIteration + "\n(" + side + ") socket and rest sizes are not equal at price " + price
            + "\n-->rest: " + restSize + ", socket: " + socketSize;

            assertEquals(restNumOrders, socketNumOrders, numOrdersFailMessage);
            assertEquals(restSize.stripTrailingZeros(), socketSize.stripTrailingZeros(), sizeFailMessage);

            if(restNumOrders != socketNumOrders){
                System.out.println(numOrdersFailMessage);
                numDisparities ++;
            }
            if(restSize.compareTo(socketSize) != 0){
                System.out.println(sizeFailMessage);
                numDisparities ++;
            }

        }
        String red = "\u001B[31m";
		String resetColor = "\u001B[0m";
        String green = "\u001B[32m";
        
        String checkMark = numDisparities == 0 ? "\u2713":"\u274C";
        String msgColor = numDisparities ==0 ?  green : red;

        System.out.println(msgColor + numDisparities + " " + side + " disparities " + checkMark + resetColor);
    }


    public static BigDecimal getRestOrders(TreeMap<BigDecimal, List<Order>> bidTree,
        TreeMap<BigDecimal, List<Order>> askTree) throws CoinbaseException {

        OrderBookRestRequest response = new OrderBookRestRequest();

        OrderBook.populateOrderBook(response.bids, "buy", bidTree);
        OrderBook.populateOrderBook(response.asks, "ask", askTree);

        return response.sequence;

    }
}

