package liveorderbook;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.erc.coinbase.pro.exceptions.CoinbaseException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;


public class AggregationTest {

    static AggregatedBook l2;
    static AggregatedBook l3;

    final static CyclicBarrier gate = new CyclicBarrier(3);

    static TestBook tb = new TestBook(URI.create("wss://ws-feed.pro.coinbase.com"));
    

   
    @BeforeAll
    static void initAll() throws CoinbaseException, InterruptedException, BrokenBarrierException {
        // l2 = OrderBookRestRequest.L2RestRequest();
        // l3 = aggregateL3Request();
        //
        //startThreads();

        tb.connect();
    }

    @BeforeEach
    void beforeEachTest(RepetitionInfo repetitionInfo) throws CoinbaseException, InterruptedException
    {
        String testIteration = "Test #" + repetitionInfo.getCurrentRepetition() + " of "
        + repetitionInfo.getTotalRepetitions();

        System.out.println(testIteration + "\n");

        Thread.sleep(5000);

        findSequenceMatch();

        System.out.println("---Level 2---");
        System.out.println("ASKS");
        printAggregation(l2.getAsks());
        System.out.println("\nBIDS");
        printAggregation(l2.getBids());

        System.out.println("\n\n---Level 3---");
        System.out.println("ASKS");
        printAggregation(l3.getAsks());
        System.out.println("\nBIDS");
        printAggregation(l3.getBids());
    }

    @AfterEach
    public void afterEachTest() throws IOException {

        tb.bidsLog.clear();
        tb.asksLog.clear();
        System.out.println("===================================");
    }
    @AfterAll
    public static void afterAll() {
        System.out.println("Closing socket");

        tb.close();
    }

    @RepeatedTest(10)
    void isAggregationCorrect(RepetitionInfo repetitionInfo)
    {
		String resetColor = "\u001B[0m";
        String green = "\u001B[32m";
        String checkMark  = "\u2713";
        
        areSequencesEqual();
        areAsksEqual();
        areBidsEqual();

        System.out.println("\n" + green + "Aggregation is correct " + checkMark.toUpperCase() + resetColor);

        // assertAll("isAggregationCorrect", () -> areSequencesEqual(),() ->areAsksEqual(), () -> areBidsEqual());

    }

    
    void areSequencesEqual() {

        assertEquals(l2.getSequence(), l3.getSequence(), "sequence not equal" );

    }

    
    void areAsksEqual() {
       
        for(int i = 0 ; i < l2.getAsks().length; i++){
            assertArrayEquals(l2.getAsks()[i], l3.getAsks()[i], "asks not equal at level " + i);
        }

    }

   
    void areBidsEqual() {
     
        for(int i = 0 ; i < l2.getBids().length; i++){
            assertArrayEquals(l2.getBids()[i], l3.getBids()[i], "bids not equal at level " + i);
        }

    }

    
    
	static void findSequenceMatch() throws CoinbaseException, InterruptedException
    {
       
        l2 = OrderBookRestRequest.L2RestRequest();
        BigDecimal restSequence = new BigDecimal(l2.getSequence());

        if(tb.asksLog.get(restSequence) == null || tb.bidsLog.get(restSequence) == null)

        {
            BigDecimal sequenceDelta = restSequence.subtract(tb.sequence);     
            System.out.println("Last logged socket sequence " + tb.sequence + " (\u0394" + sequenceDelta + ")");
        }


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
                
                // We must wait before making another rest request so as not to exceed rate limit
                Thread.sleep(500);

                l2 = OrderBookRestRequest.L2RestRequest();
                restSequence = new BigDecimal(l2.getSequence());
            }
            Thread.sleep(1);
        }
      
        System.out.println("\nComparing at sequence: " + restSequence + "\n");

        // Construct aggregated book from websocket level 3 trees
        TreeMap<BigDecimal, List<Order>> asksAtRestSequence = new TreeMap<BigDecimal, List<Order>>();
        TreeMap<BigDecimal, List<Order>> bidsAtRestSequence = new TreeMap<BigDecimal, List<Order>>();

        String failed = "asks";

        try {
            asksAtRestSequence.putAll(tb.asksLog.get(restSequence));
            failed = "bids";
            bidsAtRestSequence.putAll(tb.bidsLog.get(restSequence));
        } catch (NullPointerException e) {
            System.out.println("null pointer on " + failed + "AtRestSequence.putAll(): if the specified map is null or the specified map contains a null key and this map does not permit null keys");
            
            System.out.println("Sequence we tried to look up: " + restSequence);
            
            boolean wasNull = failed.equals("asks")? asksAtRestSequence.containsKey(restSequence):bidsAtRestSequence.containsKey(restSequence);
            if(wasNull) System.out.println("We tried to insert a null snapshot"); else System.out.println("We tried to insert an existing snapshot");
        }
      
        String[][] aggregatedAsks = aggregateTree(asksAtRestSequence, "ask");
        String[][] aggregatedBids = aggregateTree(bidsAtRestSequence, "bid");

        // l3 =  new AggregatedBook(restSequence.toString(), aggregatedAsks, aggregatedBids);
        l3 =  new AggregatedBook(restSequence.toPlainString(), aggregatedAsks, aggregatedBids);


    }

    static AggregatedBook aggregateL3Request() throws CoinbaseException {
        TreeMap<BigDecimal, List<Order>> bidTree = new  TreeMap<BigDecimal, List<Order>>();
        TreeMap<BigDecimal, List<Order>> askTree =  new TreeMap<BigDecimal, List<Order>>();

        String l3Sequence = getRestOrdersL3(bidTree, askTree);

        String[][] aggregatedAsks = aggregateTree(askTree, "ask");

        String[][] aggregatedBids = aggregateTree(bidTree, "bid");

        
        return new AggregatedBook(l3Sequence.toString(), aggregatedAsks, aggregatedBids);

    }

    static String getRestOrdersL3(TreeMap<BigDecimal, List<Order>> bidTree, TreeMap<BigDecimal, List<Order>> askTree)
            throws CoinbaseException {
        OrderBookRestRequest response = new OrderBookRestRequest();

        OrderBook.populateOrderBook(response.bids, "buy", bidTree);
        OrderBook.populateOrderBook(response.asks, "ask", askTree);

        return response.sequence.toString();

    }

    static String[][] aggregateTree(TreeMap<BigDecimal, List<Order>> tree, String side) {
        String[][] aggregatedTree = new String[50][3];

        NavigableSet<BigDecimal> prices = side.equals("bid") ? tree.descendingKeySet() : tree.navigableKeySet();

        int counter = 0;
        for (BigDecimal price : prices) {
            if (counter >= 50) {
                break;
            }

            List<Order> orders = tree.get(price);
            BigDecimal size = BigDecimal.ZERO;
            int numOrders = orders.size();
            for (Order order : orders) {
                size = size.add(order.size);
            }
            aggregatedTree[counter][0] = price.toString();
            // aggregatedTree[counter][1] = size.stripTrailingZeros().toString(); //size.toString(); 
            aggregatedTree[counter][1] = size.stripTrailingZeros().toPlainString(); //size.toString(); 
            aggregatedTree[counter][2] = Integer.toString(numOrders); // String.valueOf(numOrders);

            counter++;

        }

        return aggregatedTree;

    }

    static void printAggregation(String[][] levels) {
       
        for(int i = 0 ; i <5 ; i++){
            System.out.println(Arrays.toString(levels[i]));
        }
    }


/**
 * 
 
    static Thread l2Thread = new Thread() {
        public void run() {
            System.out.println("L2 thread");
            try {
                gate.await();

                l2 = OrderBookRestRequest.L2RestRequest();
                System.out.println("we got here? l2");

            } catch (InterruptedException | BrokenBarrierException e) {
                System.out.println("L2 thread catach " + e);

                e.printStackTrace();
            }
            // do stuff
            catch (CoinbaseException e) {
                System.out.println("L2 thread catach " + e);

                e.printStackTrace();
            }

        }
    };


    static Thread l3Thread = new Thread() {
        public void run() {
            System.out.println("L3 thread");

            try {
                System.out.println("Hey there");

                gate.await();
                l3 = aggregateL3Request();
                System.out.println("we got here? l3");

            } catch (InterruptedException | BrokenBarrierException e) {
                System.out.println("L3 thread catach " + e);

                e.printStackTrace();
            } catch (CoinbaseException e) {
                System.out.println("L3 thread catach " + e);

                e.printStackTrace();
            }

        }
    };

    static void startThreads() throws InterruptedException, BrokenBarrierException
    {
        l3Thread.start();
        l2Thread.start();
        gate.await();
        System.out.println("all threads started");
        Thread.sleep(2000);
    }

*/

}

