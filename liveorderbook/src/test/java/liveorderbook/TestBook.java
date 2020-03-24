package liveorderbook;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TestBook extends OrderBook {
   
    public LinkedHashMap<BigDecimal, HashMap<BigDecimal, List<Order>>> asksLog = new LinkedHashMap<BigDecimal, HashMap<BigDecimal, List<Order>>>() {
        protected boolean removeEldestEntry(Map.Entry<BigDecimal, HashMap<BigDecimal, List<Order>>> eldest) {
            return size() > MAX;
        }
    };
    public LinkedHashMap<BigDecimal, HashMap<BigDecimal, List<Order>>> bidsLog = new LinkedHashMap<BigDecimal, HashMap<BigDecimal, List<Order>>>() {
        protected boolean removeEldestEntry(Map.Entry<BigDecimal, HashMap<BigDecimal, List<Order>>> eldest) {
            return size() > MAX;
        }
    };
    public static final int MAX = 150;


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
            if(asksLog.get(sequence.subtract(BigDecimal.ONE)) == null || bidsLog.get(sequence.subtract(BigDecimal.ONE)) == null)
            {
                //System.out.println("Book did not change but one (or both) of the logs does maps to null for the previous sequence " + sequence.subtract(BigDecimal.ONE) );
                log();
            }
            else
            {
                asksLog.put(sequence,asksLog.get(sequence.subtract(BigDecimal.ONE)));
                bidsLog.put(sequence,bidsLog.get(sequence.subtract(BigDecimal.ONE)));
            }
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