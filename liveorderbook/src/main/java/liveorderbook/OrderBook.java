package liveorderbook;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import com.google.gson.Gson;
import org.erc.coinbase.pro.exceptions.CoinbaseException;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.json.simple.JsonObject;
import org.json.simple.Jsoner;



public class OrderBook extends WebSocketClient {
	TreeMap<BigDecimal, List<Order>> asks = new TreeMap<BigDecimal, List<Order>>();
	TreeMap<BigDecimal, List<Order>> bids = new TreeMap<BigDecimal, List<Order>>();

	BigDecimal sequence = new BigDecimal("-1"); 


	Queue<JsonObject> msgQueue = new LinkedList<>();

	Boolean printSnapShot = true;

	Boolean bookChanged = false;

	public OrderBook(URI serverUri, Draft draft) {
		super(serverUri, draft);
	}

	public OrderBook(URI serverURI) {
		super(serverURI);
	}

	public OrderBook(URI serverUri, Map<String, String> httpHeaders) {
		super(serverUri, httpHeaders);
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		Subscribe obj = new Subscribe();
		Gson gson = new Gson();
		String json = gson.toJson(obj);

		send(json);

		sequence = new BigDecimal("-2");


		System.out.println("opened connection");
	}

	@Override
	public void onMessage(String message) {
		JsonObject msg = Jsoner.deserialize(message, new JsonObject());
		try {
			processOrderMessage(msg);
		} catch (CoinbaseException e) {
			System.out.print(e);
			e.printStackTrace();
		}
	}

	public void onSequenceGap(BigDecimal start, BigDecimal end) throws CoinbaseException {
		System.out.println(String.format("Error: messages missing (%s - %s). Re-initializing book", start, end));

		loadFullOrderBook();


	}
	
	

	public void loadFullOrderBook() throws CoinbaseException {
		
		Queue<JsonObject> msgQueue = new LinkedList<>();
		sequence = new BigDecimal("-1");


		OrderBookRestRequest response = new OrderBookRestRequest();

		asks = new TreeMap<BigDecimal, List<Order>>();
		bids = new TreeMap<BigDecimal, List<Order>>();

        OrderBook.populateOrderBook(response.bids, "buy", bids);
		OrderBook.populateOrderBook(response.asks, "ask", asks);
		
		sequence = response.sequence;

		// System.out.println("Loaded Full Order Book From Rest Request. SEQUENCE IS NOW: " + sequence);

		while(msgQueue.size()>0){
			System.out.println("proccessing a message from the q");

			JsonObject msg = msgQueue.remove();
			processOrderMessage((msg));
		}

	}

	
	public static void populateOrderBook(List<List<String>> orders, String orderSide, TreeMap<BigDecimal,List<Order>> tree){
		for(int i = 0 ; i<orders.size();i++){
			String id = orders.get(i).get(2);
			String side = orderSide;
			BigDecimal price = new BigDecimal(orders.get(i).get(0));
			BigDecimal size = new BigDecimal(orders.get(i).get(1));
			
			Order order = new Order(id,side,price,size);

			addOrderToTree(tree, order);
		}
	}

	public Order msgToOrder(JsonObject msg) {
		String id = msg.containsKey("order_id") ? msg.getString("order_id") : msg.getString("id"); 
		String side = msg.getString("side");
		BigDecimal price = msg.getBigDecimal("price");

		BigDecimal size = msg.containsKey("size") ? msg.getBigDecimal("size") : msg.getBigDecimal("remaining_size");

		return new Order(id, side, price, size);
	}

	public void processOrderMessage(JsonObject msg) throws CoinbaseException {
		
		BigDecimal socketSequence = msg.getBigDecimalOrDefault("sequence", new BigDecimal("-1"));
        /*
		A sequence less than zero indicates that we are processing a rest API request for the full orderbook
        */
		if (sequence.compareTo(BigDecimal.ZERO) == -1) {
			// While we are proccessing rest request, add messages to queue to process later
			System.out.println("adding message to the queue");
			msgQueue.add(msg);
		}
		if (sequence.compareTo(new BigDecimal("-2")) == 0) {
			// A sequence of -2 indicates that we need to perform our initial load of the full orderbook
			System.out.println("Performing initial load of the full orderbook");
			loadFullOrderBook();

			bookChanged = true;

			return;
		}
		if (sequence.compareTo(new BigDecimal("-1"))==0) {
			// A sequence of -1 indicates that we are in the middle of processing the rest API request for the full order book
			System.out.println("A sequence of -1 ---> processing rest request");
			bookChanged = false;

			return;
		}

		if(socketSequence.compareTo(sequence)<=0)
		{
			// Discard sequence numbers before or equal to the sequence number returned by the rest API request 
			bookChanged = false;
			return;
		}

		if(socketSequence.compareTo(sequence.add(BigDecimal.ONE))!= 0)
		{
			// Dropped a message, start a resync process
			onSequenceGap(sequence, socketSequence);
			bookChanged = true;
			return;
		}

		sequence = socketSequence;

		if (msg.containsKey("type")) {
			String type = msg.getString("type");
			if (type.equals("open")) {
				add(msg);
				bookChanged = true;
			} else if (type.equals("done") && msg.containsKey("price")) {
				remove(msg);
			} else if (type.equals("match")) {
				match(msg);
				bookChanged = true;
			} else if (type.equals("change")) {
				System.out.println("change");
				change(msg);
			}
			else{
				bookChanged = false;
			}
		}

		if(printSnapShot && bookChanged)
		{
			printBestOrders(5);
		}

	}

	public void printBestOrders(int n){
		String red = "\u001B[31m";
		String resetColor = "\u001B[0m";
		String green = "\u001B[32m";
		
		String[] topOrders = new String[n];

		int counter = 0;
		for(BigDecimal price : asks.keySet()){
			if(counter>=n){
				break;
			}
			topOrders[counter] = red + price.setScale(2,RoundingMode.HALF_EVEN) + resetColor + "\t\t";
			counter++;
		}

		counter = 0;
		for(BigDecimal price : bids.descendingKeySet()){
			if(counter>=n){
				break;
			}
			topOrders[counter] += green + price.setScale(2,RoundingMode.HALF_EVEN) + resetColor;
			counter++;
		}

		System.out.println("\n");

		for(String order: topOrders){
			System.out.println(order);
		}

		System.out.println("\n");


	}

	public void add(JsonObject msg) {

		Order order = msgToOrder(msg);

		// Add order to tree coresponding with order.side
		if (order.side.equals("buy"))
		{
			addOrderToTree(bids, order);
		}
		else
		{
			addOrderToTree(asks, order);
		}


	}

	public static void addOrderToTree(TreeMap<BigDecimal, List<Order>> tree, Order order) {
		if (tree.get(order.price) == null) {
			List<Order> orderList = new ArrayList<>();
			orderList.add(order);
			tree.put(order.price, orderList);

		} else {
			List<Order> newOrderList = tree.get(order.price);
			newOrderList.add(order);
			tree.put(order.price, newOrderList);
		}
	}

	public void remove(JsonObject msg){
	
		Order order = msgToOrder(msg);
		// Remove order from tree coresponding with order.side
		if(order.side.equals("buy"))
		{
			removeOrderFromTree(bids, order, msg.getBigDecimal("sequence"));
		}  else {
			removeOrderFromTree(asks, order, msg.getBigDecimal("sequence"));
		}
	}


	public void removeOrderFromTree(TreeMap<BigDecimal, List<Order>> tree, Order order, BigDecimal seqNum) {

		if (tree.get(order.price) != null) {
			// Initilize a the list to hold the orders that do not match the order ID we want to delete
			List<Order> newOrdersAtPrice = new ArrayList<Order>(); 

			for (Order o : tree.get(order.price)) {
				// add to list if order does not match the order id we want to remove 
				if (!order.id.equals(o.id)) {
					newOrdersAtPrice.add(o);
				}
			}
            /*
			Set orders at this price to the list of orders at this price that are not equal to the order ID we want to remove.
            If there are no more orders at this price remove it from the tree
            */
			if (newOrdersAtPrice.size() > 0)
			{
				tree.put(order.price, newOrdersAtPrice);
			}
			else
			{
				tree.remove(order.price);
			}
			bookChanged = true;
		}
		else
		{
			bookChanged = false;
		}
	}

	public void match(JsonObject msg){
		Order matchOrder = msgToOrder(msg);
		matchOrder.id = msg.getString("maker_order_id");
		TreeMap<BigDecimal,List<Order>> tree = matchOrder.side.equals("buy") ? bids : asks;

		// Ignore if price is not in order book
		if(tree.containsKey(matchOrder.price)){
	
			/*
			Iterate across each order at this price. 
			If we find a matching orderID to the match order we received as a parameter --> Check the size of the match order. 
			If the size of the match is equal to the size of the order --> Remove the order from the list because a match will result in a size of zero
			Else --> update size of order 
			*/
			
			// List of orders at the given price
			List<Order> orders = tree.get(matchOrder.price);

			for(int i = 0; i < orders.size(); i++){
				if(orders.get(i).id.equals(matchOrder.id))
				{
					if(orders.get(i).size.compareTo(matchOrder.size) == 0){
						// Remove this order from our list of orders at this price because match will result in size of zero
						orders.remove(i);
					}
					else{
						// Update size of order 
						BigDecimal newSize = orders.get(i).size.subtract(matchOrder.size);
						Order updatedOrder = new Order(matchOrder.id,matchOrder.side,matchOrder.price,newSize); 
						orders.set(i,updatedOrder);
					}
                    /*
					Set orders at this price to our updated order list resulting from the match
                    If the match results in no more orders at this price remove it from the tree
                    */
					if(orders.size()>0) {
						tree.put(matchOrder.price,orders);
					 } 
					 else
					 {
						tree.remove(matchOrder.price);
					 } 

					// Break from the loop as we have found the match order and updated the order book accordingly 
					break;

				}
			}
		}
	}

	public void change(JsonObject msg){
		BigDecimal newSize = msg.getBigDecimalOrDefault("new_size",null);
		if(newSize == null) return;

		BigDecimal price = msg.getBigDecimalOrDefault("price",null);
		
		if(price == null) 
		{
			/*
			Any change message where the price is null indicates that the change message is for a market order.
        	Change messages for limit orders will always have a price specified.
        	Change massages for market orders are ignored as they are never in the order book.
			*/
			bookChanged = false;
			return;
		}
		
		TreeMap<BigDecimal,List<Order>> tree = msg.getString("side").equals("buy") ? bids : asks;
		List<Order> orders = tree.get(msg.getBigDecimal("price"));
		if(orders == null){
			// There are no orders at this price so there is nothing to change. 
			bookChanged = false;
			return;
		}
		int indexOfOrderID = -1;
		for(int i = 0; i<orders.size();i++){
			if(orders.get(i).id.equals(msg.getString("order_id")))
			{
				indexOfOrderID = i;
				break;
			}
		}
		if(indexOfOrderID == -1)
		{
			/*
			We receive a change message when there are no matching IDs for received but not yet open orders
            These can be ignored as they are never in the order book.
			*/
			bookChanged = false;
			return;
		}

		// Create a new order with all the same values as the previous but updating size to equal new size. 
		Order newOrder = new Order(orders.get(indexOfOrderID).id,orders.get(indexOfOrderID).side,orders.get(indexOfOrderID).price,newSize);
		// Update orders list
		orders.set(indexOfOrderID,newOrder);
		// Update Tree 
		tree.put(newOrder.price,orders);
		
		bookChanged = true;

		
	}


	@Override
	public void onClose( int code, String reason, boolean remote ) {
		System.out.println( "Connection closed by " + ( remote ? "remote peer" : "us" ) + " Code: " + code + " Reason: " + reason );
	}

	@Override
	public void onError( Exception ex ) {
		ex.printStackTrace();
		// if the error is fatal then onClose will be called additionally
		System.out.println("onError");
	}

}