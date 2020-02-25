package liveorderbook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

import org.erc.coinbase.pro.Client;
import org.erc.coinbase.pro.exceptions.CoinbaseException;
import org.erc.coinbase.pro.rest.ClientConfig;
import org.erc.coinbase.pro.rest.RESTClient;
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
    @RepeatedTest(5)
    public void isOrderBookCorrect(RepetitionInfo repetitionInfo) throws InterruptedException, CoinbaseException {
        String testIteration = "Repetition #" + repetitionInfo.getCurrentRepetition() + " of "
                + repetitionInfo.getTotalRepetitions();

        System.out.println(testIteration + "\n");

        TreeMap<BigDecimal, List<Order>> restAsks = new TreeMap<BigDecimal, List<Order>>();
        TreeMap<BigDecimal, List<Order>> restBids = new TreeMap<BigDecimal, List<Order>>();

        BigDecimal restSequence = getRestOrders(restBids, restAsks);

        if((!tb.asksLog.containsKey(restSequence) && !tb.bidsLog.containsKey(restSequence)))
        {
            BigDecimal sequencesBehind = restSequence.subtract(tb.sequence);
            System.out.println("order logs do not contain sequence " + restSequence + ". curr sequence is " + tb.sequence + " (delta " + sequencesBehind + ")" );            
        }


        while (!tb.asksLog.containsKey(restSequence) && !tb.bidsLog.containsKey(restSequence)) {
           
            BigDecimal sequencesBehind = restSequence.subtract(tb.sequence);
            if(sequencesBehind.compareTo(BigDecimal.ZERO)<0)
            {
                /**
                Our order logs do not contain the sequence returned by the rest request 
                and sequencesBehind is a negative number
                
                This means that the order logs do not contain a sequence 
                This is not because we havent proccessed the sequence message
                But because its so far back that our size-limited linkedhashmap that stores the order snap shots has deleted it already
                
                In this case we should load another rest request to test against
                */
                System.out.println("Our order logs do not contain the sequence returned by the rest request and sequencesBehind is a negative number. As a result, making another rest request to test against.");
                restAsks.clear();;
                restBids.clear();
                Thread.sleep(500);

                restSequence = getRestOrders(restBids, restAsks);
                System.out.println("new rest sequence: " + restSequence + " websocket sequence is " + tb.sequence + ". " + restSequence.subtract(tb.sequence)+ " sequences behind");
            }
            Thread.sleep(1);
        }
      

       System.out.println(
                    "order logs DOES contain sequence " + restSequence + ". curr sequence is " + tb.sequence);

        System.out.println();

        TreeMap<BigDecimal, List<Order>> asksAtRestSequence = new TreeMap<BigDecimal, List<Order>>();
        asksAtRestSequence.putAll(tb.asksLog.get(restSequence));

        TreeMap<BigDecimal, List<Order>> bidsAtRestSequence = new TreeMap<BigDecimal, List<Order>>(
                tb.bidsLog.get(restSequence));
        

        Set<BigDecimal> socketAskPrices = asksAtRestSequence.keySet();
        Set<BigDecimal> restAskPrices = restAsks.keySet();

        Set<BigDecimal> socketBidPrices = bidsAtRestSequence.keySet();
        Set<BigDecimal> restBidPrices = restBids.keySet();


        // printPriceSizeDiff(socketAskPrices, restAskPrices, "ask");
        // printPriceSizeDiff(socketBidPrices, restBidPrices, "bid");

        // System.out.println("");
        // Set<BigDecimal> socketAskPricesRemoved = new HashSet<BigDecimal>(socketAskPrices);
        // socketAskPricesRemoved.removeAll(restAskPrices);
        // System.out.println("socket ask prices not in rest asks: " + socketAskPricesRemoved);

        // Set<BigDecimal> restAskPricesRemoved = new HashSet<BigDecimal>(restAskPrices);
        // restAskPricesRemoved.removeAll(socketAskPrices);
        // System.out.println("rest ask prices not in websocket asks: " + restAskPricesRemoved);
        // System.out.println("");
        

        assertEquals(restAskPrices, socketAskPrices, testIteration);
        assertEquals(restBidPrices, socketBidPrices, testIteration);

        System.out.println("");
        assertOrderListsAtEachPriceLevel(testIteration, restSequence, "ask",asksAtRestSequence,restAsks);
        assertOrderListsAtEachPriceLevel(testIteration, restSequence, "bid",bidsAtRestSequence,restBids);
        System.out.println("");


        String snapshotTime = getTime();

        try {
            String dir = TestResultManager.createTestResultDir("test" + repetitionInfo.getCurrentRepetition());
            TestResultManager.writeTestResultToFile(dir, "ask", restSequence, asksAtRestSequence, restAsks,
                    snapshotTime);
            TestResultManager.writeTestResultToFile(dir, "bid", restSequence, bidsAtRestSequence, restBids,
                    snapshotTime);
            

        } catch (IOException e) {
            // TODO Auto-generated catch block
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
        
        String checkMark = numDisparities == 0? "\u2713":"\u274C";
        String msgColor = numDisparities ==0?  green : red;

        System.out.println(msgColor + numDisparities + " " + side + " disparities at rest sequence " + seq + " " + checkMark + resetColor);
    }

    void printPriceSizeDiff(Set<BigDecimal> socket, Set<BigDecimal> rest, String side) {
        System.out.println("Length of rest " + side + "s: " + rest.size());
        System.out.println("Length of websocket " + side + "s: " + socket.size());
    }

    public static BigDecimal getRestOrders(TreeMap<BigDecimal, List<Order>> bidTree,
            TreeMap<BigDecimal, List<Order>> askTree) throws CoinbaseException {

        OrderBookRestRequest response = new OrderBookRestRequest();

        OrderBook.populateOrderBook(response.bids, "buy", bidTree);
        OrderBook.populateOrderBook(response.asks, "ask", askTree);

        return response.sequence;

    }

    public String getTime() throws CoinbaseException {
        ClientConfig config = new ClientConfig();
        config.setBaseUrl("https://api.pro.coinbase.com");
        config.setPublicKey("add28ba503bf0ce93180e0a47b6c36be");
        config.setSecretKey("uiBn7USapxsSLHTkPQokSrk9cvTH8pzEUnBq6QeVmO8m+/+tEAa3F5YsuL43ZLviKcxx3+F2vnMaH+inqKx24g==");
        config.setPassphrase("9moja018n4a");

        Client client = new RESTClient(config);

        Date date = client.getTime();
        DateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss:SSS");
        format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
        String formatted = format.format(date);
        format.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        formatted = format.format(date);
        System.out.println(formatted);
        return formatted;
    }

}

class TestBook extends OrderBook {
   
    LinkedHashMap<BigDecimal, HashMap<BigDecimal, List<Order>>> asksLog = new LinkedHashMap<BigDecimal, HashMap<BigDecimal, List<Order>>>() {
        protected boolean removeEldestEntry(Map.Entry<BigDecimal, HashMap<BigDecimal, List<Order>>> eldest) {
            return size() > MAX;
        }
    };
    LinkedHashMap<BigDecimal, HashMap<BigDecimal, List<Order>>> bidsLog = new LinkedHashMap<BigDecimal, HashMap<BigDecimal, List<Order>>>() {
        protected boolean removeEldestEntry(Map.Entry<BigDecimal, HashMap<BigDecimal, List<Order>>> eldest) {
            return size() > MAX;
        }
    };
    static final int MAX = 150;


    public TestBook(URI serverURI) {
        super(serverURI);
        printSnapShot = false;
    }

    @Override
    public void onMessage(String message) {
        super.onMessage(message);
        

        if(bookChanged)
        {
            log();
        }
        else{
            asksLog.put(sequence,asksLog.get(sequence.subtract(BigDecimal.ONE)));
            bidsLog.put(sequence,bidsLog.get(sequence.subtract(BigDecimal.ONE)));

        }
        
    }

    void log() {
        HashMap<BigDecimal,List<Order>> currAskTree = new  HashMap<BigDecimal,List<Order>>();
        HashMap<BigDecimal,List<Order>> currBidTree = new  HashMap<BigDecimal,List<Order>>();

        copyTree(currAskTree, asks);
        copyTree(currBidTree,bids);

        bidsLog.put(sequence, currBidTree);
        asksLog.put(sequence, currAskTree);
    }

    void copyTree(HashMap<BigDecimal,List<Order>> copy,TreeMap<BigDecimal,List<Order>> tree)
    {
        for(BigDecimal price : tree.keySet())
        {
            List<Order> orderList = new ArrayList<>();
            for(Order order : tree.get(price)){
                orderList.add(new Order(order.id,order.side,order.price,order.size));
            }
            copy.put(price,orderList);
        }
    }

}