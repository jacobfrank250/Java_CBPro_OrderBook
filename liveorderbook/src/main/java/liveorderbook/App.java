package liveorderbook;

import java.net.URI;

/**
 * Hello world!
 *
 */
public class App 
{
  
    public static void main( String[] args ){
		// OrderBook ob = new OrderBook( new URI( "wss://ws-feed.pro.coinbase.com" )); // more about drafts here: http://github.com/TooTallNate/Java-WebSocket/wiki/Drafts
		OrderBook ob = new OrderBook(URI.create("wss://ws-feed.pro.coinbase.com")); // more about drafts here: http://github.com/TooTallNate/Java-WebSocket/wiki/Drafts

		ob.connect();

	}
}
